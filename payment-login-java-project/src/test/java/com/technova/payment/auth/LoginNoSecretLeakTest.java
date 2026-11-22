package com.technova.payment.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** SEC-06 (login never prints credentials) and SEC-07 (no secrets committed in this project's sources). */
class LoginNoSecretLeakTest {

    private static final String DEMO_USERNAME = "demo.user";

    @Test
    void loginWritesNothingToStandardOutputOrErrorForSuccessOrFailure() {
        UserRepository repository = new UserRepository();
        String password = repository.findByUsername(DEMO_USERNAME).orElseThrow().getPassword();
        LoginService service = new LoginService(repository);

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            service.login(DEMO_USERNAME, password);
            service.login(DEMO_USERNAME, "wrong-password-value");
            service.login("no.such.user", "another-wrong-value");
            service.login(null, null);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String captured = out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
        assertEquals("", captured, "login must not print anything");
        assertFalse(captured.contains(password));
    }

    @Test
    void sourceFilesContainNoSecretsOutsideTheDocumentedSyntheticFixture() throws IOException {
        // Maven runs tests with the module directory as the working directory.
        Path root = Path.of("").toAbsolutePath();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root.resolve("src/main"))) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        files.add(root.resolve("pom.xml"));

        Pattern keyShapes = Pattern.compile("(sk-[A-Za-z0-9_-]{10,}|AKIA[0-9A-Z]{12,}|ghp_[A-Za-z0-9]{20,}|eyJ[A-Za-z0-9_-]{20,})");
        Pattern assignment = Pattern.compile("(?i)\\b(password|passwd|secret|api[_-]?key|token)\\s*[:=]\\s*\"[^\"]+\"");
        List<String> findings = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            String text = Files.readString(file);
            if (keyShapes.matcher(text).find()) {
                findings.add(name + ": key-shaped string");
            }
            if (assignment.matcher(text).find()) {
                findings.add(name + ": credential assignment");
            }
        }
        assertTrue(findings.isEmpty(), "unexpected secret-like content: " + findings);
    }

    @Test
    void theOnlyCredentialInTheSourcesIsTheDocumentedSyntheticFixture() throws IOException {
        Path repositoryFile = Path.of("src/main/java/com/technova/payment/auth/UserRepository.java").toAbsolutePath();
        String text = Files.readString(repositoryFile);

        long fixtureCount = Pattern.compile("users\\.put\\(").matcher(text).results().count();
        assertEquals(1, fixtureCount, "exactly one fixture account is expected");
        assertTrue(text.contains("Synthetic demo account only"), "the fixture must stay labelled synthetic");
    }
}
