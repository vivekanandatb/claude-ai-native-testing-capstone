package com.technova.payment.auth;

public class User {
    private final String username;
    private final String password;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Starter-only model for the exercise. Do not use plain-text passwords
     * in a production application.
     */
    public String getPassword() {
        return password;
    }
}
