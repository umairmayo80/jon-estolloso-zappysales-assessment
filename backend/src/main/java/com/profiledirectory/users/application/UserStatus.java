package com.profiledirectory.users.application;

import com.profiledirectory.shared.error.InvalidRequestException;
import java.util.Locale;

public enum UserStatus {
    ACTIVE("active"),
    DELETED("deleted"),
    ALL("all");

    private final String apiValue;

    UserStatus(String apiValue) { this.apiValue = apiValue; }
    public String apiValue() { return apiValue; }

    public static UserStatus parse(String value) {
        String normalized = value == null ? "active" : value.trim().toLowerCase(Locale.ROOT);
        for (UserStatus status : values()) {
            if (status.apiValue.equals(normalized)) {
                return status;
            }
        }
        throw new InvalidRequestException("status must be one of: active, deleted, all");
    }
}
