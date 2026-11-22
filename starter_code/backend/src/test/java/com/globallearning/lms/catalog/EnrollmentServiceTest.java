package com.globallearning.lms.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.globallearning.lms.catalog.domain.Course;
import com.globallearning.lms.catalog.domain.Enrollment;
import com.globallearning.lms.catalog.domain.EnrollmentStatus;
import com.globallearning.lms.catalog.domain.Learner;
import com.globallearning.lms.catalog.repository.EnrollmentRepository;
import com.globallearning.lms.catalog.service.CourseNotFoundException;
import com.globallearning.lms.catalog.service.CourseNotOpenException;
import com.globallearning.lms.catalog.service.CourseService;
import com.globallearning.lms.catalog.service.DuplicateEnrollmentException;
import com.globallearning.lms.catalog.service.EnrollmentService;
import com.globallearning.lms.catalog.service.LearnerNotFoundException;
import com.globallearning.lms.catalog.service.LearnerService;
import com.globallearning.lms.catalog.service.SeatUnavailableException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Business-rule unit tests for EnrollmentService (ENR-03, ENR-04, ENR-05, ENR-08).
 * Repositories are mocked, so these prove the service's own logic, not database integrity.
 */
class EnrollmentServiceTest {

    private EnrollmentRepository enrollments;
    private CourseService courses;
    private LearnerService learners;
    private EnrollmentService service;
    private Course course;
    private Learner learner;

    @BeforeEach
    void setUp() {
        enrollments = mock(EnrollmentRepository.class);
        courses = mock(CourseService.class);
        learners = mock(LearnerService.class);
        service = new EnrollmentService(enrollments, courses, learners);

        course = CatalogTestData.course("CS1007", true);
        course.setCapacity(2);
        learner = CatalogTestData.learner("L-00001");
        when(courses.getByCode("CS1007")).thenReturn(course);
        when(learners.getByRef("L-00001")).thenReturn(learner);
        when(enrollments.findByCourseAndLearnerAndStatus(course, learner, EnrollmentStatus.ACTIVE)).thenReturn(Optional.empty());
        when(enrollments.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- ENR-03: capacity boundary ---------------------------------------------------------------

    @Test
    void enrollsWhenOneSeatIsStillFree() {
        when(enrollments.countByCourseAndStatus(course, EnrollmentStatus.ACTIVE)).thenReturn(1L);   // capacity 2

        Enrollment saved = service.enroll("CS1007", "L-00001");

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollments).save(captor.capture());
        assertThat(saved.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(captor.getValue().getCourse()).isSameAs(course);
        assertThat(captor.getValue().getLearner()).isSameAs(learner);
    }

    @Test
    void rejectsWhenActiveSeatsEqualCapacity() {
        when(enrollments.countByCourseAndStatus(course, EnrollmentStatus.ACTIVE)).thenReturn(2L);   // seats == capacity

        assertThatThrownBy(() -> service.enroll("CS1007", "L-00001")).isInstanceOf(SeatUnavailableException.class);
        verify(enrollments, never()).save(any());
    }

    @Test
    void rejectsWhenCourseIsAlreadyOverCapacity() {
        when(enrollments.countByCourseAndStatus(course, EnrollmentStatus.ACTIVE)).thenReturn(5L);   // seats > capacity (as in the demo seed)

        assertThatThrownBy(() -> service.enroll("CS1007", "L-00001")).isInstanceOf(SeatUnavailableException.class);
        verify(enrollments, never()).save(any());
    }

    /** CHARACTERIZATION: the source skips the seat check when capacity is null. The DB column is NOT NULL, so this is defensive code. */
    @Test
    void nullCapacitySkipsTheSeatCheck_characterization() {
        course.setCapacity(null);
        when(enrollments.countByCourseAndStatus(course, EnrollmentStatus.ACTIVE)).thenReturn(1_000L);

        assertThat(service.enroll("CS1007", "L-00001").getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    // ---- ENR-04: inactive course -------------------------------------------------------------------

    @Test
    void rejectsAnInactiveCourseBeforeLookingUpTheLearner() {
        course.setActive(false);

        assertThatThrownBy(() -> service.enroll("CS1007", "L-00001")).isInstanceOf(CourseNotOpenException.class);
        verifyNoInteractions(learners);
        verify(enrollments, never()).save(any());
    }

    // ---- ENR-05: duplicate (repeated submission) ----------------------------------------------------

    @Test
    void repeatedSubmissionForTheSameActiveEnrollmentIsRejectedAndNothingIsSaved() {
        Enrollment existing = new Enrollment(course, learner, EnrollmentStatus.ACTIVE);
        when(enrollments.findByCourseAndLearnerAndStatus(course, learner, EnrollmentStatus.ACTIVE)).thenReturn(Optional.of(existing));
        when(enrollments.countByCourseAndStatus(course, EnrollmentStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.enroll("CS1007", "L-00001")).isInstanceOf(DuplicateEnrollmentException.class);
        verify(enrollments, never()).save(any());
    }

    // ---- ENR-08: order of checks ----------------------------------------------------------------------

    @Test
    void unknownCourseIsReportedBeforeAnythingElse() {
        when(courses.getByCode("GHOST")).thenThrow(new CourseNotFoundException("GHOST"));

        assertThatThrownBy(() -> service.enroll("GHOST", "L-99999")).isInstanceOf(CourseNotFoundException.class);
        verifyNoInteractions(learners);
    }

    @Test
    void inactiveCourseIsReportedBeforeAnUnknownLearner() {
        course.setActive(false);
        when(learners.getByRef("L-99999")).thenThrow(new LearnerNotFoundException("L-99999"));

        assertThatThrownBy(() -> service.enroll("CS1007", "L-99999")).isInstanceOf(CourseNotOpenException.class);
    }

    @Test
    void unknownLearnerOnAnOpenCourseIsReported() {
        when(learners.getByRef("L-99999")).thenThrow(new LearnerNotFoundException("L-99999"));

        assertThatThrownBy(() -> service.enroll("CS1007", "L-99999")).isInstanceOf(LearnerNotFoundException.class);
        verify(enrollments, never()).save(any());
    }
}
