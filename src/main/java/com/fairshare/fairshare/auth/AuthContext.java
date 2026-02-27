package com.fairshare.fairshare.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthContext {
    public static final String USER_ID_HEADER = "X-User-Id";

    private final boolean required;

    public AuthContext(@Value("${fairshare.auth.required:true}") boolean required) {
        this.required = required;
    }

    public Long getActorUserId(HttpServletRequest request) {
        String value = request.getHeader(USER_ID_HEADER);
        if (value == null || value.isBlank()) {
            if (required) {
                throw new UnauthenticatedException("Missing required header: " + USER_ID_HEADER);
            }
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) {
                throw new UnauthenticatedException(USER_ID_HEADER + " must be a positive integer");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new UnauthenticatedException(USER_ID_HEADER + " must be a valid integer");
        }
    }
}
