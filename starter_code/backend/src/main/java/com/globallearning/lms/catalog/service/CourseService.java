package com.globallearning.lms.catalog.service;

import com.globallearning.lms.catalog.audit.CatalogAuditLogger;
import com.globallearning.lms.catalog.domain.Course;
import com.globallearning.lms.catalog.domain.CourseLevel;
import com.globallearning.lms.catalog.repository.CourseRepository;
import java.util.List;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CatalogAuditLogger auditLogger;

    public CourseService(CourseRepository courseRepository, CatalogAuditLogger auditLogger) {
        this.courseRepository = courseRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public List<Course> listActive(CourseLevel level, String keyword) {
        List<Course> results;
        if (keyword != null && !keyword.isBlank()) {
            if (level != null) {
                results = courseRepository.searchByKeywordAndLevel(keyword.trim(), level.name());
            } else {
                results = courseRepository.searchByKeyword(keyword.trim());
            }
        } else if (level != null) {
            results = courseRepository.findByActiveTrueAndLevelAndArchivedAtNullOrderByCodeAsc(level);
        } else {
            results = courseRepository.findByActiveTrueAndArchivedAtNullOrderByCodeAsc();
        }
        auditLogger.recordCatalogRead("anonymous", results.size());
        return results;
    }

    @Transactional(readOnly = true)
    public Course getByCode(String code) {
        return courseRepository.findByCodeIgnoreCase(code)
            .orElseThrow(() -> new CourseNotFoundException(code));
    }

    @Transactional
    public Course update(String code, String title, String description, Integer credits, Integer capacity, String instructorEmail) {
        Course course = getByCode(code);
        if (course.getArchivedAt() != null) {
            throw new CourseArchivedException(code);
        }
        course.setTitle(title);
        course.setDescription(description);
        course.setCredits(credits);
        course.setCapacity(capacity);
        course.setInstructorEmail(instructorEmail);
        return courseRepository.save(course);
    }

    @Transactional
    public Course archive(String code, String reason) {
        Course course = getByCode(code);
        if (course.getArchivedAt() != null) {
            throw new CourseAlreadyArchivedException(code);
        }
        course.setArchivedAt(Instant.now());
        course.setArchiveReason(reason);
        Course saved = courseRepository.save(course);
        auditLogger.recordCourseArchived("anonymous", course.getCode(), reason);
        return saved;
    }

    // TODO(platform-team): once EnrolmentService lands, the catalogue should refuse to
    // deactivate a course that still has open enrolments or an active waitlist.
    @Transactional
    public Course deactivate(String code) {
        Course course = getByCode(code);
        course.setActive(false);
        return courseRepository.save(course);
    }

    @Transactional
    public Course reactivate(String code) {
        Course course = getByCode(code);
        if (course.isActive()) {
            throw new CourseAlreadyActiveException(code);
        }
        course.setActive(true);
        Course saved = courseRepository.save(course);
        auditLogger.recordCourseReactivated("anonymous", course.getCode());
        return saved;
    }
}
