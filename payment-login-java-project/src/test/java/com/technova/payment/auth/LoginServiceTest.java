package com.technova.payment.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LoginServiceTest {

    // The only synthetic account defined by the repository (UserRepository).
    private static final String DEMO_USERNAME = "demo.user";

    private LoginService service;
    private String demoPassword;

    @BeforeEach
    void setUp() {
        UserRepository repository = new UserRepository();
        service = new LoginService(repository);
        // Read the synthetic password from the fixture instead of duplicating it in the test.
        demoPassword = repository.findByUsername(DEMO_USERNAME).orElseThrow().getPassword();
    }

    @Test
    void loginWithValidCredentialsShouldSucceed() {
        assertTrue(service.login(DEMO_USERNAME, demoPassword));
    }

    @Test
    void loginWithInvalidPasswordShouldFail() {
        assertFalse(service.login(DEMO_USERNAME, "not-the-password"));
    }

    @Test
    void loginWithUnknownUsernameShouldFail() {
        assertFalse(service.login("no.such.user", demoPassword));
    }

    @Test
    void unknownUserAndWrongPasswordAreIndistinguishable() {
        boolean unknownUser = service.login("no.such.user", "whatever");
        boolean wrongPassword = service.login(DEMO_USERNAME, "whatever");
        assertFalse(unknownUser);
        assertFalse(wrongPassword);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void blankOrNullUsernameIsRejectedWithoutException(String username) {
        assertFalse(service.login(username, demoPassword));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void blankOrNullPasswordIsRejectedWithoutException(String password) {
        assertFalse(service.login(DEMO_USERNAME, password));
    }

    @Test
    void bothInputsNullIsRejectedWithoutException() {
        assertFalse(service.login(null, null));
    }

    @Test
    void passwordIsCaseSensitive() {
        assertFalse(service.login(DEMO_USERNAME, demoPassword.toUpperCase()));
        assertFalse(service.login(DEMO_USERNAME, demoPassword.toLowerCase()));
    }

    @Test
    void usernameIsCaseSensitive() {
        assertFalse(service.login("DEMO.USER", demoPassword));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "' demo.user'|leading space",
        "'demo.user '|trailing space"
    })
    void usernameWithSurroundingWhitespaceIsNotMatched(String username, String description) {
        assertFalse(service.login(username, demoPassword), description);
    }

    @Test
    void passwordWithTrailingCharacterOrPrefixIsRejected() {
        assertFalse(service.login(DEMO_USERNAME, demoPassword + "x"));
        assertFalse(service.login(DEMO_USERNAME, demoPassword.substring(0, demoPassword.length() - 1)));
    }

    @Test
    void sqlInjectionStyleInputIsTreatedAsPlainText() {
        assertFalse(service.login("' OR '1'='1", "' OR '1'='1"));
        assertFalse(service.login(DEMO_USERNAME, "' OR '1'='1"));
    }

    @Test
    void constructorRejectsNullRepository() {
        assertThrows(IllegalArgumentException.class, () -> new LoginService(null));
    }
}
