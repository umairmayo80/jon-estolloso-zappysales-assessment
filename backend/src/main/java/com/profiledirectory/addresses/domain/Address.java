package com.profiledirectory.addresses.domain;

import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.shared.persistence.BaseEntity;
import com.profiledirectory.users.domain.UserProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "addresses")
public class Address extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false, updatable = false)
    private UserProfile userProfile;

    @Column(nullable = false, length = 80)
    private String label;
    @Column(nullable = false, length = 180)
    private String line1;
    @Column(length = 180)
    private String line2;
    @Column(nullable = false, length = 120)
    private String city;
    @Column(length = 120)
    private String region;
    @Column(length = 32)
    private String postalCode;
    @Column(nullable = false, length = 2)
    private String countryCode;
    @Column(name = "is_primary", nullable = false)
    private boolean primary;
    @Column(nullable = false)
    private int displayOrder;
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private AdminAccount deletedBy;

    protected Address() {
    }

    public Address(
            UserProfile userProfile,
            String label,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            boolean primary,
            int displayOrder) {
        this.userProfile = userProfile;
        this.label = label;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.region = region;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.primary = primary;
        this.displayOrder = displayOrder;
    }

    public UserProfile getUserProfile() { return userProfile; }
    public String getLabel() { return label; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getRegion() { return region; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public boolean isPrimary() { return primary; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getDeletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }

    public void update(
            String label,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            boolean primary,
            int displayOrder) {
        this.label = label;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.region = region;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.primary = primary;
        this.displayOrder = displayOrder;
    }

    public void unsetPrimary() { this.primary = false; }
    public void softDelete(AdminAccount actor, Instant at) { this.deletedAt = at; this.deletedBy = actor; }
    public void restore() { this.deletedAt = null; this.deletedBy = null; }
}
