package com.profiledirectory.addresses.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Size(max = 180) String line1,
        @Size(max = 180) String line2,
        @NotBlank @Size(max = 120) String city,
        @Size(max = 120) String region,
        @Size(max = 32) String postalCode,
        @NotBlank @Pattern(regexp = "(?i)^[A-Z]{2}$", message = "must be a two-letter ISO country code") String countryCode,
        boolean primary,
        @Min(0) @Max(100000) Integer displayOrder) {
    @Override
    public String toString() {
        return "AddressRequest[redacted]";
    }
}
