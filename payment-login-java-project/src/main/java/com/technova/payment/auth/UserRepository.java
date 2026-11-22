package com.technova.payment.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UserRepository {
    private final Map<String, User> users = new HashMap<>();

    public UserRepository() {
        // Synthetic demo account only; replace with secure storage in production.
        users.put("demo.user", new User("demo.user", "DemoPass123!"));
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }
}
