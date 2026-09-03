package com.profiledirectory.shared.web;

import com.profiledirectory.shared.error.PreconditionFailedException;
import com.profiledirectory.shared.error.PreconditionRequiredException;
import java.util.UUID;

public final class EntityTag {
    private EntityTag() {
    }

    public static String forUser(UUID id, long version) {
        return "\"user-" + id + "-v" + version + "\"";
    }

    public static String forAddress(UUID id, long version) {
        return "\"address-" + id + "-v" + version + "\"";
    }

    public static void requireMatch(String provided, String current) {
        if (provided == null || provided.isBlank()) {
            throw new PreconditionRequiredException("If-Match is required for a modifying request");
        }
        if (!provided.trim().equals(current)) {
            throw new PreconditionFailedException("This record changed since you last loaded it");
        }
    }
}
