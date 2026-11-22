package com.technova.payment.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Authenticates a user against a {@link UserRepository}.
 *
 * Behavior:
 * - null or blank username/password returns false (no exception).
 * - unknown user and wrong password are indistinguishable to the caller (both false).
 * - the password comparison is constant-time and case-sensitive.
 * - the username is matched exactly (no trimming, no case folding).
 * - credentials are never logged or included in exception messages.
 */
public class LoginService {

    private final UserRepository userRepository;

    public LoginService() {
        this(new UserRepository());
    }

    public LoginService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("userRepository must not be null");
        }
        this.userRepository = userRepository;
    }

    public boolean login(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            return false;
        }

        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            return false;
        }

        byte[] expected = user.get().getPassword().getBytes(StandardCharsets.UTF_8);
        byte[] supplied = password.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
