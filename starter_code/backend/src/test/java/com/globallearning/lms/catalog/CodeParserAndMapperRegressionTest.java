package com.globallearning.lms.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globallearning.lms.catalog.domain.Course;
import com.globallearning.lms.catalog.domain.CourseLevel;
import com.globallearning.lms.catalog.service.LegacyCourseCodeParser;
import com.globallearning.lms.catalog.web.mapper.CourseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/** REG-02, REG-03, REG-04: expected values were read from LegacyCourseCodeParser and CourseMapper, not guessed. */
class CodeParserAndMapperRegressionTest {

    private final LegacyCourseCodeParser parser = new LegacyCourseCodeParser();
    private final CourseMapper mapper = new CourseMapper(parser);

    @ParameterizedTest(name = "facultyOf({0}) = {1}")
    @CsvSource({"cs2110,CS", "DATA1105,DATA", "ENGG2104B,ENGG", "1234,UNKNOWN"})
    void facultyIsTheUppercasedLeadingLetters(String code, String expected) {
        assertThat(parser.facultyOf(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void facultyOfNullOrEmptyIsUnknown(String code) {
        assertThat(parser.facultyOf(code)).isEqualTo("UNKNOWN");
    }

    @ParameterizedTest(name = "levelOf({0}) = {1}")
    @CsvSource({
        "CS0100,FOUNDATION",      // band 0
        "CS1999,FOUNDATION",      // band 1 (upper edge)
        "CS2000,INTERMEDIATE",    // band 2
        "CS3000,ADVANCED",        // band 3 (lower edge of advanced)
        "CS9999,ADVANCED",        // band 9
        "CS1,FOUNDATION",         // shorter than 5 characters
        "12345,FOUNDATION",       // no leading letters
        "ABCDE12345,FOUNDATION",  // more than 4 leading letters
        "CSX1234,FOUNDATION"      // character after the letters is not a digit
    })
    void levelIsDerivedFromTheFirstDigitAfterTheFacultyLetters(String code, CourseLevel expected) {
        assertThat(parser.levelOf(code)).isEqualTo(expected);
    }

    @Test
    void levelOfNullIsFoundation() {
        assertThat(parser.levelOf(null)).isEqualTo(CourseLevel.FOUNDATION);
    }

    @Test
    void summaryIsSerialisedWithoutDescriptionEmailOrArchiveFields() throws Exception {
        Course course = CatalogTestData.course("CS2110", true);
        course.setInstructorEmail("teacher@example.com");
        course.setArchiveReason("synthetic reason");

        String summaryJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(mapper.toSummary(course));

        assertThat(summaryJson).contains("\"code\":\"CS2110\"", "\"level\"", "\"credits\"");
        assertThat(summaryJson).doesNotContain("description", "instructorEmail", "archive");
    }

    @Test
    void detailResponseCarriesFacultyDerivedFromTheCode() {
        assertThat(mapper.toResponse(CatalogTestData.course("DATA1105", true)).getFaculty()).isEqualTo("DATA");
    }
}
