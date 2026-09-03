package com.profiledirectory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.profiledirectory.addresses.domain.Address;
import com.profiledirectory.addresses.domain.AddressRepository;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import com.profiledirectory.users.domain.UserProfile;
import com.profiledirectory.users.domain.UserProfileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DevDemoDataInitializerTest {
    private static final Instant ARCHIVED_PROFILE_AT = Instant.parse("2025-01-15T09:30:00Z");
    private static final Instant MAYA_ARCHIVED_ADDRESS_AT = Instant.parse("2025-02-14T10:15:30Z");

    @Mock private UserProfileRepository profiles;
    @Mock private AddressRepository addresses;
    @Mock private AdminAccountRepository admins;

    private AppSecurityProperties properties;
    private DevDemoDataInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new AppSecurityProperties();
        properties.getBootstrap().setAdminEmail("admin@example.test");
        initializer = new DevDemoDataInitializer(properties, profiles, addresses, admins);
    }

    @Test
    void leavesAllRepositoriesUntouchedWhenDemoSeedingIsDisabled() {
        properties.setSeedDemoData(false);

        initializer.run(new DefaultApplicationArguments());

        verifyNoInteractions(profiles, addresses, admins);
    }

    @Test
    void createsExactlySixtyProfilesAndSeventyNineAddressesWithExpectedArchiveStates() {
        properties.setSeedDemoData(true);
        SeedStore store = installStore();
        AdminAccount bootstrapAdmin = new AdminAccount(
                "admin@example.test", "not-a-real-password-hash", "Directory Administrator");
        when(admins.findByEmail("admin@example.test")).thenReturn(Optional.of(bootstrapAdmin));

        initializer.run(new DefaultApplicationArguments());

        assertThat(store.profilesByEmail).hasSize(60);
        assertThat(store.profilesByEmail.keySet()).allMatch(email -> email.endsWith("@example.test"));
        assertThat(store.profilesByEmail.values()).filteredOn(UserProfile::isDeleted).hasSize(6);
        assertThat(store.profilesByEmail.values()).filteredOn(profile -> !profile.isDeleted()).hasSize(54);
        assertThat(store.profilesByEmail.values())
                .filteredOn(UserProfile::isDeleted)
                .extracting(UserProfile::getDeletedAt)
                .containsOnly(ARCHIVED_PROFILE_AT);

        assertThat(store.addresses).hasSize(79);
        assertThat(store.addresses).filteredOn(Address::isDeleted).hasSize(1);
        assertThat(store.addresses)
                .filteredOn(Address::isDeleted)
                .extracting(Address::getDeletedAt)
                .containsOnly(MAYA_ARCHIVED_ADDRESS_AT);
        assertThat(store.addresses)
                .filteredOn(address -> address.isPrimary() && !address.isDeleted())
                .hasSize(60);
        assertThat(store.addresses)
                .filteredOn(address -> !address.isPrimary() && !address.isDeleted())
                .hasSize(18);

        UserProfile maya = store.profilesByEmail.get("maya.chen@example.test");
        assertThat(maya).isNotNull();
        assertThat(maya.getFirstName()).isEqualTo("Maya");
        assertThat(maya.getLastName()).isEqualTo("Chen");
        assertThat(maya.isDeleted()).isFalse();
        assertThat(store.addressesFor(maya))
                .filteredOn(address -> !address.isDeleted())
                .hasSize(2);
        assertThat(store.addressesFor(maya))
                .filteredOn(Address::isDeleted)
                .singleElement()
                .satisfies(address -> {
                    assertThat(address.getLabel()).isEqualTo("Travel");
                    assertThat(address.isPrimary()).isFalse();
                });
        verify(admins).findByEmail("admin@example.test");
    }

    @Test
    void seedsArchivedFixturesWhenTheBootstrapAdminIsIntentionallyUnavailable() {
        properties.setSeedDemoData(true);
        SeedStore store = installStore();
        when(admins.findByEmail("admin@example.test")).thenReturn(Optional.empty());

        initializer.run(new DefaultApplicationArguments());

        assertThat(store.profilesByEmail.values()).filteredOn(UserProfile::isDeleted).hasSize(6);
        assertThat(store.addresses).filteredOn(Address::isDeleted).hasSize(1);
    }

    @Test
    void isIdempotentAndNeverOverwritesOrRearchivesExistingFixtureRecords() {
        properties.setSeedDemoData(true);
        SeedStore store = installStore();
        when(admins.findByEmail("admin@example.test")).thenReturn(Optional.empty());

        initializer.run(new DefaultApplicationArguments());
        UserProfile maya = store.profilesByEmail.get("maya.chen@example.test");
        UserProfile archivedProfile = store.profilesByEmail.get("arthur.reed@example.test");
        Address mayaHome = store.addressesFor(maya).stream()
                .filter(address -> address.getLabel().equals("Home"))
                .findFirst()
                .orElseThrow();
        Address mayaTravel = store.addressesFor(maya).stream()
                .filter(address -> address.getLabel().equals("Travel"))
                .findFirst()
                .orElseThrow();

        maya.update(maya.getEmail(), "Maya Edited", "Chen");
        mayaHome.update(
                mayaHome.getLabel(),
                mayaHome.getLine1(),
                "Manually updated suite",
                mayaHome.getCity(),
                mayaHome.getRegion(),
                mayaHome.getPostalCode(),
                mayaHome.getCountryCode(),
                mayaHome.isPrimary(),
                mayaHome.getDisplayOrder());
        archivedProfile.restore();
        mayaTravel.restore();

        initializer.run(new DefaultApplicationArguments());

        assertThat(store.profilesByEmail).hasSize(60);
        assertThat(store.addresses).hasSize(79);
        assertThat(maya.getFirstName()).isEqualTo("Maya Edited");
        assertThat(mayaHome.getLine2()).isEqualTo("Manually updated suite");
        assertThat(archivedProfile.isDeleted()).isFalse();
        assertThat(mayaTravel.isDeleted()).isFalse();
        verify(profiles, times(60)).save(any(UserProfile.class));
        verify(addresses, times(79)).save(any(Address.class));
    }

    private SeedStore installStore() {
        SeedStore store = new SeedStore();
        when(profiles.findByEmail(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(store.profilesByEmail.get(invocation.getArgument(0, String.class))));
        when(profiles.save(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0, UserProfile.class);
            store.profilesByEmail.put(profile.getEmail(), profile);
            return profile;
        });
        when(addresses.existsByUserProfileIdAndLabel(any(UUID.class), anyString())).thenAnswer(invocation -> {
            UUID profileId = invocation.getArgument(0, UUID.class);
            String label = invocation.getArgument(1, String.class);
            return store.addresses.stream().anyMatch(address ->
                    address.getUserProfile().getId().equals(profileId) && address.getLabel().equals(label));
        });
        when(addresses.save(any(Address.class))).thenAnswer(invocation -> {
            Address address = invocation.getArgument(0, Address.class);
            store.addresses.add(address);
            return address;
        });
        return store;
    }

    private static final class SeedStore {
        private final Map<String, UserProfile> profilesByEmail = new LinkedHashMap<>();
        private final List<Address> addresses = new ArrayList<>();

        private List<Address> addressesFor(UserProfile profile) {
            return addresses.stream()
                    .filter(address -> address.getUserProfile().getId().equals(profile.getId()))
                    .toList();
        }
    }
}
