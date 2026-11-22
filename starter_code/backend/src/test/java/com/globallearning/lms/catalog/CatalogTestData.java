package com.globallearning.lms.catalog;

import com.globallearning.lms.catalog.domain.Course;
import com.globallearning.lms.catalog.domain.CourseLevel;
import com.globallearning.lms.catalog.domain.Learner;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Isolated, deterministic, synthetic fixtures shared by the catalogue tests.
 * No real names, emails, or credentials. Addresses use the reserved example.com domain.
 */
final class CatalogTestData {

    private CatalogTestData() {
    }

    static Course course(String code, boolean active) {
        return new Course(code, "Synthetic course " + code, "Synthetic description.", CourseLevel.FOUNDATION, 10, active);
    }

    static Learner learner(String ref) {
        return new Learner(ref, "Learner", "Synthetic", ref.toLowerCase() + "@example.com");
    }

    /** A valid body for PUT /api/courses/{code} (CourseUpdateRequest). */
    static Map<String, Object> validUpdateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Foundations of Programming");
        body.put("description", "Basics.");
        body.put("credits", 10);
        body.put("capacity", 30);
        body.put("instructorEmail", "teacher@example.com");
        return body;
    }

    static Map<String, Object> updateBodyWith(String field, Object value) {
        Map<String, Object> body = validUpdateBody();
        body.put(field, value);
        return body;
    }

    static String repeat(String s, int n) {
        return s.repeat(n);
    }

    /** A syntactically valid email of exactly {@code total} characters (local 64 + '@' + domain). */
    static String emailOfLength(int total) {
        String local = "a".repeat(64);
        int domainLen = total - local.length() - 1;           // after '@'
        String label = "b".repeat(domainLen - ".com".length());
        return local + "@" + label + ".com";
    }
}
