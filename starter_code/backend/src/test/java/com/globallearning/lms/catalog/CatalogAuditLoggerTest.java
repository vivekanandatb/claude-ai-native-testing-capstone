package com.globallearning.lms.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.globallearning.lms.catalog.audit.CatalogAuditLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** SEC-05: what the audit logger actually writes. Uses the real logger, captures console output, no files. */
@ExtendWith(OutputCaptureExtension.class)
class CatalogAuditLoggerTest {

    private final CatalogAuditLogger audit = new CatalogAuditLogger();

    @Test
    void archiveEventIsWrittenInTheDocumentedKeyValueFormat(CapturedOutput output) {
        audit.recordCourseArchived("anonymous", "CS2110", "retired");

        assertThat(output.getOut()).contains("actor=anonymous action=COURSE_ARCHIVED code=CS2110 reason=retired");
    }

    @Test
    void catalogReadEventRecordsTheResultCount(CapturedOutput output) {
        audit.recordCatalogRead("anonymous", 7);

        assertThat(output.getOut()).contains("actor=anonymous action=CATALOG_READ results=7");
    }

    /**
     * CHARACTERIZATION of known defect HIGH-04 (reports/defects.md DEF-02): a newline inside the free-text
     * archive reason is written verbatim, so a caller can make a forged audit entry appear on its own line.
     * This documents today's behavior and is NOT an endorsement. Flip it when the reason is sanitised.
     */
    @Test
    void newlineInArchiveReasonProducesAForgedLogLine_knownDefect(CapturedOutput output) {
        audit.recordCourseArchived("anonymous", "CS2110", "ok\nactor=admin action=COURSE_ARCHIVED code=CS9999 reason=forged");

        boolean forgedLineStartsAtColumnZero = output.getOut().lines().anyMatch(l -> l.startsWith("actor=admin action=COURSE_ARCHIVED"));
        assertThat(forgedLineStartsAtColumnZero)
            .as("the injected text appears as its own log line (defect still present)")
            .isTrue();
    }
}
