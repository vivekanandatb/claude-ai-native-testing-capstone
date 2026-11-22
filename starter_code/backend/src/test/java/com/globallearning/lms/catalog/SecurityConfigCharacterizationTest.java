package com.globallearning.lms.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.globallearning.lms.catalog.config.SecurityConfig;
import com.globallearning.lms.catalog.domain.Enrollment;
import com.globallearning.lms.catalog.domain.EnrollmentStatus;
import com.globallearning.lms.catalog.service.CourseService;
import com.globallearning.lms.catalog.service.EnrollmentService;
import com.globallearning.lms.catalog.service.LegacyCourseCodeParser;
import com.globallearning.lms.catalog.web.CourseController;
import com.globallearning.lms.catalog.web.EnrollmentController;
import com.globallearning.lms.catalog.web.mapper.CourseMapper;
import com.globallearning.lms.catalog.web.mapper.EnrollmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SEC-01: CHARACTERIZATION OF A KNOWN DEFECT, NOT AN ENDORSEMENT.
 *
 * These tests load the REAL SecurityConfig with the security filter chain switched ON (the other test
 * classes switch it off). They send state-changing requests with no credentials and no CSRF token.
 * Today every one succeeds, because SecurityConfig permits all requests and disables CSRF
 * (security review finding HIGH-01, reports/defects.md DEF-01).
 *
 * When authentication is added these tests are expected to FAIL. That is the signal to replace them with
 * assertions of 401/403, NOT to delete them or loosen them.
 */
@WebMvcTest(controllers = {CourseController.class, EnrollmentController.class})
@Import({SecurityConfig.class, CourseMapper.class, LegacyCourseCodeParser.class, EnrollmentMapper.class})
class SecurityConfigCharacterizationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private EnrollmentService enrollmentService;

    @Test
    void anonymousUserCanUpdateACourse_knownDefect() throws Exception {
        given(courseService.update(any(), any(), any(), any(), any(), any())).willReturn(CatalogTestData.course("CS1007", true));

        mockMvc.perform(put("/api/courses/CS1007").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hijacked\",\"credits\":10,\"capacity\":30}"))
            .andExpect(status().isOk());
    }

    @Test
    void anonymousUserCanArchiveAndDeactivateACourse_knownDefect() throws Exception {
        given(courseService.archive(any(), any())).willReturn(CatalogTestData.course("CS1007", false));
        given(courseService.deactivate(any())).willReturn(CatalogTestData.course("CS1007", false));

        mockMvc.perform(post("/api/courses/CS1007/archive").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"synthetic\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/CS1007/deactivate"))
            .andExpect(status().isOk());
    }

    @Test
    void anonymousUserCanCreateAnEnrollmentWithoutACsrfToken_knownDefect() throws Exception {
        given(enrollmentService.enroll(any(), any())).willReturn(
            new Enrollment(CatalogTestData.course("CS1007", true), CatalogTestData.learner("L-00001"), EnrollmentStatus.ACTIVE));

        mockMvc.perform(post("/api/enrollments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseCode\":\"CS1007\",\"learnerRef\":\"L-00001\"}"))
            .andExpect(status().isCreated());
    }
}
