package com.technova.payment.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * LOGIN-08 (boundary inputs) and MS-07 (non-breaking space). Expected results were derived from LoginService:
 * null/blank (String.isBlank) -> false; otherwise exact, case-sensitive, constant-time comparison.
 */
class LoginBoundaryTest {

    private static final String DEMO_USERNAME = "demo.user";

    private LoginService service;
    private String demoPassword;

    @BeforeEach
    void setUp() {
        UserRepository repository = new UserRepository();
        service = new LoginService(repository);
        demoPassword = repository.findByUsername(DEMO_USERNAME).orElseThrow().getPassword();
    }

    @Test
    void veryLongUsernameAndPasswordAreRejectedQuicklyWithoutAnException() {
        String huge = "x".repeat(100_000);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertFalse(service.login(huge, demoPassword));
            assertFalse(service.login(DEMO_USERNAME, huge));
            assertFalse(service.login(huge, huge));
        });
    }

    @ParameterizedTest(name = "non-ASCII or control input is rejected without exception: {index}")
    @ValueSource(strings = {"ünïcödé", "密码测试", "pass\u0000word", "emoji-😀", "\u202Eevil"})
    void unusualCharactersAreRejectedWithoutAnException(String input) {
        assertDoesNotThrow(() -> assertFalse(service.login(input, demoPassword)));
        assertDoesNotThrow(() -> assertFalse(service.login(DEMO_USERNAME, input)));
    }

    /**
     * MS-07: String.isBlank() uses Character.isWhitespace, which is false for U+00A0 (no-break space).
     * So a no-break-space-only value is NOT treated as blank by the guard. The comparison still rejects it,
     * so the observable result is the same (false); this pins that behavior.
     */
    @Test
    void noBreakSpaceOnlyInputIsNotBlankButIsStillRejected() {
        assertFalse("\u00A0".isBlank(), "precondition: NBSP is not blank under String.isBlank()");

        assertFalse(service.login(DEMO_USERNAME, "\u00A0"));
        assertFalse(service.login("\u00A0", demoPassword));
    }

    @Test
    void thePasswordWithSurroundingNoBreakSpacesIsRejected() {
        assertFalse(service.login(DEMO_USERNAME, "\u00A0" + demoPassword));
        assertFalse(service.login(DEMO_USERNAME, demoPassword + "\u00A0"));
    }

    @Test
    void theCorrectCredentialsStillSucceedAfterManyFailures_noLockoutExists_characterization() {
        // LOGIN-09: no lockout or rate limit is implemented (open question Q2). This pins today's behavior.
        for (int i = 0; i < 1_000; i++) {
            assertFalse(service.login(DEMO_USERNAME, "wrong-" + i));
        }
        assertTrue(service.login(DEMO_USERNAME, demoPassword));
    }
}
