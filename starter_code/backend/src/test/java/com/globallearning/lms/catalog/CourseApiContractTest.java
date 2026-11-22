package com.globallearning.lms.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globallearning.lms.catalog.domain.Course;
import com.globallearning.lms.catalog.service.CourseAlreadyArchivedException;
import com.globallearning.lms.catalog.service.CourseArchivedException;
import com.globallearning.lms.catalog.service.CourseNotFoundException;
import com.globallearning.lms.catalog.service.CourseService;
import com.globallearning.lms.catalog.service.LegacyCourseCodeParser;
import com.globallearning.lms.catalog.web.CourseController;
import com.globallearning.lms.catalog.web.mapper.CourseMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract tests for the course endpoints. Mock-only: no database, no network.
 * Scenario IDs refer to docs/test-matrix.md.
 */
@WebMvcTest(CourseController.class)
@AutoConfigureMockMvc(addFilters = false)   // security is exercised separately in SecurityConfigCharacterizationTest
@Import({CourseMapper.class, LegacyCourseCodeParser.class})
class CourseApiContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @MockitoBean
    private CourseService courseService;

    private String body(Map<String, Object> map) throws Exception {
        return json.writeValueAsString(map);
    }

    // ---- CAT-05 / MS-01: unknown code -> 404 with the documented error body -------------------

    @Test
    void getUnknownCourseReturns404WithErrorCodeAndMessage() throws Exception {
        given(courseService.getByCode("NOPE000")).willThrow(new CourseNotFoundException("NOPE000"));

        mockMvc.perform(get("/api/courses/NOPE000"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("COURSE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("No course found with code NOPE000"));
    }

    @Test
    void writeEndpointsOnUnknownCourseReturn404() throws Exception {
        given(courseService.update(eq("GHOST"), any(), any(), any(), any(), any())).willThrow(new CourseNotFoundException("GHOST"));
        given(courseService.archive(eq("GHOST"), any())).willThrow(new CourseNotFoundException("GHOST"));
        given(courseService.deactivate("GHOST")).willThrow(new CourseNotFoundException("GHOST"));

        mockMvc.perform(put("/api/courses/GHOST").contentType(MediaType.APPLICATION_JSON)
                .content(body(CatalogTestData.validUpdateBody())))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("COURSE_NOT_FOUND"));
        mockMvc.perform(post("/api/courses/GHOST/archive").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"retired\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("COURSE_NOT_FOUND"));
        mockMvc.perform(post("/api/courses/GHOST/deactivate"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("COURSE_NOT_FOUND"));
    }

    // ---- CAT-06: update validation boundaries --------------------------------------------------

    static Stream<Arguments> invalidUpdates() {
        return Stream.of(
            Arguments.of("title blank", CatalogTestData.updateBodyWith("title", ""), "title"),
            Arguments.of("title whitespace only", CatalogTestData.updateBodyWith("title", "   "), "title"),
            Arguments.of("title null", CatalogTestData.updateBodyWith("title", null), "title"),
            Arguments.of("title 201 chars", CatalogTestData.updateBodyWith("title", CatalogTestData.repeat("t", 201)), "title"),
            Arguments.of("description 2001 chars", CatalogTestData.updateBodyWith("description", CatalogTestData.repeat("d", 2001)), "description"),
            Arguments.of("credits 4 (below min 5)", CatalogTestData.updateBodyWith("credits", 4), "credits"),
            Arguments.of("credits 61 (above max 60)", CatalogTestData.updateBodyWith("credits", 61), "credits"),
            Arguments.of("credits null", CatalogTestData.updateBodyWith("credits", null), "credits"),
            Arguments.of("capacity 0 (below min 1)", CatalogTestData.updateBodyWith("capacity", 0), "capacity"),
            Arguments.of("capacity 501 (above max 500)", CatalogTestData.updateBodyWith("capacity", 501), "capacity"),
            Arguments.of("capacity null", CatalogTestData.updateBodyWith("capacity", null), "capacity"),
            Arguments.of("email malformed", CatalogTestData.updateBodyWith("instructorEmail", "not-an-email"), "instructorEmail"),
            Arguments.of("email 121 chars", CatalogTestData.updateBodyWith("instructorEmail", CatalogTestData.emailOfLength(121)), "instructorEmail")
        );
    }

    @ParameterizedTest(name = "rejects: {0}")
    @MethodSource("invalidUpdates")
    void updateWithInvalidFieldIsRejectedBeforeReachingTheService(String label, Map<String, Object> bad, String field) throws Exception {
        mockMvc.perform(put("/api/courses/CS1007").contentType(MediaType.APPLICATION_JSON).content(body(bad)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fields." + field).exists());
        then(courseService).shouldHaveNoInteractions();
    }

    static Stream<Arguments> validBoundaryUpdates() {
        return Stream.of(
            Arguments.of("title exactly 200 chars", CatalogTestData.updateBodyWith("title", CatalogTestData.repeat("t", 200))),
            Arguments.of("description exactly 2000 chars", CatalogTestData.updateBodyWith("description", CatalogTestData.repeat("d", 2000))),
            Arguments.of("description null", CatalogTestData.updateBodyWith("description", null)),
            Arguments.of("credits exactly 5", CatalogTestData.updateBodyWith("credits", 5)),
            Arguments.of("credits exactly 60", CatalogTestData.updateBodyWith("credits", 60)),
            Arguments.of("capacity exactly 1", CatalogTestData.updateBodyWith("capacity", 1)),
            Arguments.of("capacity exactly 500", CatalogTestData.updateBodyWith("capacity", 500)),
            Arguments.of("email exactly 120 chars", CatalogTestData.updateBodyWith("instructorEmail", CatalogTestData.emailOfLength(120)))
        );
    }

    @ParameterizedTest(name = "accepts: {0}")
    @MethodSource("validBoundaryUpdates")
    void updateAtTheDocumentedLimitsIsAccepted(String label, Map<String, Object> ok) throws Exception {
        given(courseService.update(eq("CS1007"), any(), any(), any(), any(), any()))
            .willReturn(CatalogTestData.course("CS1007", true));

        mockMvc.perform(put("/api/courses/CS1007").contentType(MediaType.APPLICATION_JSON).content(body(ok)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CS1007"));
    }

    @Test
    void updateWithoutInstructorEmailIsAccepted_emailIsOptional() throws Exception {      // MS-03
        Map<String, Object> noEmail = CatalogTestData.validUpdateBody();
        noEmail.remove("instructorEmail");
        given(courseService.update(eq("CS1007"), any(), any(), any(), any(), any()))
            .willReturn(CatalogTestData.course("CS1007", true));

        mockMvc.perform(put("/api/courses/CS1007").contentType(MediaType.APPLICATION_JSON).content(body(noEmail)))
            .andExpect(status().isOk());
    }

    @Test
    void updateCallsTheServiceWithTheSubmittedValues() throws Exception {
        given(courseService.update(any(), any(), any(), any(), any(), any()))
            .willReturn(CatalogTestData.course("CS1007", true));

        mockMvc.perform(put("/api/courses/CS1007").contentType(MediaType.APPLICATION_JSON)
                .content(body(CatalogTestData.validUpdateBody())))
            .andExpect(status().isOk());

        then(courseService).should().update("CS1007", "Foundations of Programming", "Basics.", 10, 30, "teacher@example.com");
    }

    // ---- CAT-07: archive reason rules ----------------------------------------------------------

    @ParameterizedTest(name = "archive rejects reason of length {0}")
    @ValueSource(strings = {"", " ", "   "})
    void archiveWithBlankReasonIsRejected(String reason) throws Exception {
        mockMvc.perform(post("/api/courses/CS2110/archive").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("reason", reason))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fields.reason").exists());
        then(courseService).shouldHaveNoInteractions();
    }

    @Test
    void archiveWithMissingReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/courses/CS2110/archive").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.reason").exists());
    }

    @Test
    void archiveReasonOf501CharactersIsRejectedAnd500IsAccepted() throws Exception {
        mockMvc.perform(post("/api/courses/CS2110/archive").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("reason", "r".repeat(501)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.reason").exists());

        given(courseService.archive(eq("CS2110"), any())).willReturn(CatalogTestData.course("CS2110", false));
        mockMvc.perform(post("/api/courses/CS2110/archive").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("reason", "r".repeat(500)))))
            .andExpect(status().isOk());
    }

    // ---- CAT-08: conflicts ---------------------------------------------------------------------

    @Test
    void archivingAnAlreadyArchivedCourseIsAConflict() throws Exception {
        given(courseService.archive(eq("CS2110"), any())).willThrow(new CourseAlreadyArchivedException("CS2110"));

        mockMvc.perform(post("/api/courses/CS2110/archive").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"retired\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("COURSE_ALREADY_ARCHIVED"))
            .andExpect(jsonPath("$.message").value("Course already archived: CS2110"));
    }

    @Test
    void updatingAnArchivedCourseIsAConflict() throws Exception {
        given(courseService.update(eq("CS2110"), any(), any(), any(), any(), any())).willThrow(new CourseArchivedException("CS2110"));

        mockMvc.perform(put("/api/courses/CS2110").contentType(MediaType.APPLICATION_JSON)
                .content(body(CatalogTestData.validUpdateBody())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("COURSE_ARCHIVED"));
    }

    // ---- MS-08 / CAT-10: deactivate -------------------------------------------------------------

    @Test
    void deactivateReturnsTheCourseWithActiveFalse() throws Exception {
        given(courseService.deactivate("CS1007")).willReturn(CatalogTestData.course("CS1007", false));

        mockMvc.perform(post("/api/courses/CS1007/deactivate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CS1007"))
            .andExpect(jsonPath("$.active").value(false));
    }

    // ---- SEC-03: field exposure (list vs. detail) -----------------------------------------------

    private Course courseWithSensitiveFields() {
        Course c = CatalogTestData.course("CS1007", true);
        c.setInstructorEmail("teacher@example.com");
        c.setArchivedAt(Instant.parse("2020-01-01T00:00:00Z"));
        c.setArchiveReason("synthetic reason");
        return c;
    }

    @Test
    void listEndpointDoesNotExposeDescriptionInstructorEmailOrArchiveReason() throws Exception {
        given(courseService.listActive(any(), any())).willReturn(List.of(courseWithSensitiveFields()));

        mockMvc.perform(get("/api/courses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("CS1007"))
            .andExpect(jsonPath("$[0].description").doesNotExist())
            .andExpect(jsonPath("$[0].instructorEmail").doesNotExist())
            .andExpect(jsonPath("$[0].archiveReason").doesNotExist());
    }

    /**
     * CHARACTERIZATION of known defect MED-04 (reports/defects.md, DEF-04): the public detail endpoint
     * currently returns instructorEmail and archiveReason. This test documents today's behavior. It is
     * NOT an endorsement; flip it when the exposure is fixed.
     */
    @Test
    void detailEndpointExposesInstructorEmailAndArchiveReason_characterization() throws Exception {
        given(courseService.getByCode("CS1007")).willReturn(courseWithSensitiveFields());

        mockMvc.perform(get("/api/courses/CS1007"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instructorEmail").value("teacher@example.com"))
            .andExpect(jsonPath("$.archiveReason").value("synthetic reason"));
    }

    // ---- SEC-04: error bodies carry only documented fields --------------------------------------

    @Test
    void errorBodiesContainOnlyDocumentedKeysAndNoInternals() throws Exception {
        given(courseService.getByCode("NOPE000")).willThrow(new CourseNotFoundException("NOPE000"));

        String notFound = mockMvc.perform(get("/api/courses/NOPE000")).andReturn().getResponse().getContentAsString();
        String validation = mockMvc.perform(put("/api/courses/CS1007").contentType(MediaType.APPLICATION_JSON)
                .content(body(CatalogTestData.updateBodyWith("credits", 1))))
            .andReturn().getResponse().getContentAsString();

        for (String errorBody : List.of(notFound, validation)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = json.readValue(errorBody, Map.class);
            org.assertj.core.api.Assertions.assertThat(parsed.keySet()).isSubsetOf("error", "message", "fields");
            org.assertj.core.api.Assertions.assertThat(errorBody.toLowerCase())
                .doesNotContain("exception", "stacktrace", "trace", "com.globallearning");
        }
    }
}
