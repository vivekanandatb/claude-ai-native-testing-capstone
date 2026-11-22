package com.globallearning.lms.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.globallearning.lms.catalog.audit.CatalogAuditLogger;
import com.globallearning.lms.catalog.domain.Course;
import com.globallearning.lms.catalog.domain.CourseLevel;
import com.globallearning.lms.catalog.repository.CourseRepository;
import com.globallearning.lms.catalog.service.CourseAlreadyActiveException;
import com.globallearning.lms.catalog.service.CourseAlreadyArchivedException;
import com.globallearning.lms.catalog.service.CourseArchivedException;
import com.globallearning.lms.catalog.service.CourseNotFoundException;
import com.globallearning.lms.catalog.service.CourseService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for CourseService business rules (SVC-01 and state transitions). Repository and audit logger are mocked. */
class CourseServiceTest {

    private CourseRepository repository;
    private CatalogAuditLogger audit;
    private CourseService service;

    @BeforeEach
    void setUp() {
        repository = mock(CourseRepository.class);
        audit = mock(CatalogAuditLogger.class);
        service = new CourseService(repository, audit);
        when(repository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- SVC-01: which query path is used -----------------------------------------------------------

    @ParameterizedTest(name = "keyword [{0}] is ignored and the plain active list is used")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void blankKeywordFallsBackToTheNonSearchQuery(String keyword) {
        when(repository.findByActiveTrueAndArchivedAtNullOrderByCodeAsc()).thenReturn(List.of(CatalogTestData.course("CS1007", true)));

        assertThat(service.listActive(null, keyword)).hasSize(1);
        verify(repository, never()).searchByKeyword(anyString());
    }

    @Test
    void keywordIsTrimmedBeforeSearching() {
        when(repository.searchByKeyword("cs")).thenReturn(List.of(CatalogTestData.course("CS1007", true)));

        assertThat(service.listActive(null, "  cs  ")).hasSize(1);
        verify(repository).searchByKeyword("cs");
    }

    @Test
    void keywordWithLevelUsesTheCombinedSearchWithTheLevelName() {
        when(repository.searchByKeywordAndLevel("data", "FOUNDATION")).thenReturn(List.of());

        assertThat(service.listActive(CourseLevel.FOUNDATION, "data")).isEmpty();
        verify(repository).searchByKeywordAndLevel("data", "FOUNDATION");
    }

    @Test
    void levelWithoutKeywordUsesTheLevelFilter() {
        when(repository.findByActiveTrueAndLevelAndArchivedAtNullOrderByCodeAsc(CourseLevel.ADVANCED)).thenReturn(List.of());

        assertThat(service.listActive(CourseLevel.ADVANCED, null)).isEmpty();
        verify(repository).findByActiveTrueAndLevelAndArchivedAtNullOrderByCodeAsc(CourseLevel.ADVANCED);
    }

    @Test
    void everyListRecordsACatalogReadAuditEventWithTheResultCount() {
        when(repository.findByActiveTrueAndArchivedAtNullOrderByCodeAsc())
            .thenReturn(List.of(CatalogTestData.course("CS1007", true), CatalogTestData.course("CS2110", true)));

        service.listActive(null, null);

        verify(audit).recordCatalogRead("anonymous", 2);
    }

    // ---- state transitions -------------------------------------------------------------------------

    @Test
    void archivingSetsTimestampAndReasonAndAudits() {
        Course c = CatalogTestData.course("CS2110", true);
        when(repository.findByCodeIgnoreCase("CS2110")).thenReturn(Optional.of(c));

        Course archived = service.archive("CS2110", "retired");

        assertThat(archived.getArchivedAt()).isNotNull();
        assertThat(archived.getArchiveReason()).isEqualTo("retired");
        verify(audit).recordCourseArchived("anonymous", "CS2110", "retired");
    }

    @Test
    void archivingTwiceIsRejectedAndDoesNotSaveAgain() {
        Course c = CatalogTestData.course("CS2110", true);
        c.setArchivedAt(java.time.Instant.parse("2020-01-01T00:00:00Z"));
        when(repository.findByCodeIgnoreCase("CS2110")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.archive("CS2110", "again")).isInstanceOf(CourseAlreadyArchivedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updatingAnArchivedCourseIsRejected() {
        Course c = CatalogTestData.course("CS2110", true);
        c.setArchivedAt(java.time.Instant.parse("2020-01-01T00:00:00Z"));
        when(repository.findByCodeIgnoreCase("CS2110")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.update("CS2110", "New", "d", 10, 30, null)).isInstanceOf(CourseArchivedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateReplacesAllEditableFields() {
        Course c = CatalogTestData.course("CS2110", true);
        when(repository.findByCodeIgnoreCase("CS2110")).thenReturn(Optional.of(c));

        Course updated = service.update("CS2110", "New title", "New description", 20, 100, "teacher@example.com");

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getCredits()).isEqualTo(20);
        assertThat(updated.getCapacity()).isEqualTo(100);
        assertThat(updated.getInstructorEmail()).isEqualTo("teacher@example.com");
    }

    @Test
    void reactivatingAnActiveCourseIsRejected() {
        when(repository.findByCodeIgnoreCase("CS1007")).thenReturn(Optional.of(CatalogTestData.course("CS1007", true)));

        assertThatThrownBy(() -> service.reactivate("CS1007")).isInstanceOf(CourseAlreadyActiveException.class);
        verify(audit, never()).recordCourseReactivated(anyString(), anyString());
    }

    @Test
    void reactivatingAnInactiveCourseActivatesItAndAudits() {
        when(repository.findByCodeIgnoreCase("CS1007")).thenReturn(Optional.of(CatalogTestData.course("CS1007", false)));

        assertThat(service.reactivate("CS1007").isActive()).isTrue();
        verify(audit).recordCourseReactivated("anonymous", "CS1007");
    }

    @Test
    void deactivateMakesTheCourseInactive() {
        when(repository.findByCodeIgnoreCase("CS1007")).thenReturn(Optional.of(CatalogTestData.course("CS1007", true)));

        assertThat(service.deactivate("CS1007").isActive()).isFalse();
    }

    @Test
    void unknownCodeRaisesCourseNotFound() {
        when(repository.findByCodeIgnoreCase("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByCode("GHOST")).isInstanceOf(CourseNotFoundException.class)
            .hasMessage("No course found with code GHOST");
    }
}
