package com.globallearning.lms.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.globallearning.lms.catalog.domain.Enrollment;
import com.globallearning.lms.catalog.domain.EnrollmentStatus;
import com.globallearning.lms.catalog.repository.EnrollmentRepository;
import com.globallearning.lms.catalog.service.CourseNotFoundException;
import com.globallearning.lms.catalog.service.CourseNotOpenException;
import com.globallearning.lms.catalog.service.DuplicateEnrollmentException;
import com.globallearning.lms.catalog.service.EnrollmentService;
import com.globallearning.lms.catalog.service.LearnerNotFoundException;
import com.globallearning.lms.catalog.service.LearnerService;
import com.globallearning.lms.catalog.service.SeatUnavailableException;
import com.globallearning.lms.catalog.web.EnrollmentController;
import com.globallearning.lms.catalog.web.LearnerController;
import com.globallearning.lms.catalog.web.mapper.EnrollmentMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract tests for /api/enrollments and /api/learners. Mock-only: services and repository are mocked,
 * so nothing here proves database behavior (see ENR-06, blocked).
 */
@WebMvcTest(controllers = {EnrollmentController.class, LearnerController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(EnrollmentMapper.class)
class EnrollmentApiContractTest {

    private static final String REQUEST = "{\"courseCode\":\"CS1007\",\"learnerRef\":\"L-00001\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EnrollmentService enrollmentService;
    @MockitoBean
    private LearnerService learnerService;
    @MockitoBean
    private EnrollmentRepository enrollmentRepository;

    // ---- ENR-01 -----------------------------------------------------------------------------------

    @Test
    void successfulEnrollmentReturns201WithTheEnrollment() throws Exception {
        given(enrollmentService.enroll("CS1007", "L-00001")).willReturn(
            new Enrollment(CatalogTestData.course("CS1007", true), CatalogTestData.learner("L-00001"), EnrollmentStatus.ACTIVE));

        mockMvc.perform(post("/api/enrollments").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.courseCode").value("CS1007"))
            .andExpect(jsonPath("$.learnerRef").value("L-00001"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ---- ENR-02 / MS-02: error mapping ---------------------------------------------------------------

    static Stream<Arguments> enrollmentFailures() {
        return Stream.of(
            Arguments.of(new DuplicateEnrollmentException("CS1007", "L-00001"), 409, "DUPLICATE_ENROLLMENT"),
            Arguments.of(new SeatUnavailableException("CS1007"), 409, "SEAT_UNAVAILABLE"),
            Arguments.of(new CourseNotOpenException("CS1007"), 409, "COURSE_NOT_OPEN"),
            Arguments.of(new LearnerNotFoundException("L-00001"), 404, "LEARNER_NOT_FOUND"),
            // MS-02: raised by CourseService and mapped by the OTHER @RestControllerAdvice (CatalogExceptionHandler)
            Arguments.of(new CourseNotFoundException("CS1007"), 404, "COURSE_NOT_FOUND")
        );
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @MethodSource("enrollmentFailures")
    void knownEnrollmentFailuresMapToTheDocumentedStatusAndErrorCode(RuntimeException failure, int httpStatus, String errorCode) throws Exception {
        given(enrollmentService.enroll(any(), any())).willThrow(failure);

        mockMvc.perform(post("/api/enrollments").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
            .andExpect(status().is(httpStatus))
            .andExpect(jsonPath("$.error").value(errorCode));
    }

    // ---- ENR-09 / MS-06: learner history -----------------------------------------------------------

    @Test
    void learnerWithEnrollmentsGetsTheirList() throws Exception {
        var learner = CatalogTestData.learner("L-00001");
        given(learnerService.getByRef("L-00001")).willReturn(learner);
        given(enrollmentRepository.findByLearnerOrderByCreatedAtDesc(learner)).willReturn(
            List.of(new Enrollment(CatalogTestData.course("CS1007", true), learner, EnrollmentStatus.ACTIVE)));

        mockMvc.perform(get("/api/learners/L-00001/enrollments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].courseCode").value("CS1007"))
            .andExpect(jsonPath("$[0].learnerRef").value("L-00001"));
    }

    @Test
    void learnerWithNoEnrollmentsGetsAnEmptyListNotAnError() throws Exception {
        var learner = CatalogTestData.learner("L-00002");
        given(learnerService.getByRef("L-00002")).willReturn(learner);
        given(enrollmentRepository.findByLearnerOrderByCreatedAtDesc(learner)).willReturn(List.of());

        mockMvc.perform(get("/api/learners/L-00002/enrollments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownLearnerGets404() throws Exception {
        given(learnerService.getByRef("L-99999")).willThrow(new LearnerNotFoundException("L-99999"));

        mockMvc.perform(get("/api/learners/L-99999/enrollments"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("LEARNER_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("No learner found with ref L-99999"));
    }
}
