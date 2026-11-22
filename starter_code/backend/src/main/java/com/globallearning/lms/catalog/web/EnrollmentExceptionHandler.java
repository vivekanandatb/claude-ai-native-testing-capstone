package com.globallearning.lms.catalog.web;

import com.globallearning.lms.catalog.service.CourseNotOpenException;
import com.globallearning.lms.catalog.service.DuplicateEnrollmentException;
import com.globallearning.lms.catalog.service.SeatUnavailableException;
import com.globallearning.lms.catalog.service.LearnerNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class EnrollmentExceptionHandler {

    @ExceptionHandler(DuplicateEnrollmentException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "DUPLICATE_ENROLLMENT");
        return ResponseEntity.status(409).body(body);
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleSeatUnavailable() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "SEAT_UNAVAILABLE");
        return ResponseEntity.status(409).body(body);
    }

    @ExceptionHandler(CourseNotOpenException.class)
    public ResponseEntity<Map<String, Object>> handleCourseNotOpen() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "COURSE_NOT_OPEN");
        return ResponseEntity.status(409).body(body);
    }

    @ExceptionHandler(LearnerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleLearnerNotFound(LearnerNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "LEARNER_NOT_FOUND");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(404).body(body);
    }
}
