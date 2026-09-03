package com.profiledirectory.addresses.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.profiledirectory.addresses.api.AddressRequest;
import com.profiledirectory.addresses.api.AddressResponse;
import com.profiledirectory.addresses.domain.Address;
import com.profiledirectory.addresses.domain.AddressRepository;
import com.profiledirectory.audit.application.AuditService;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import com.profiledirectory.users.domain.UserProfile;
import com.profiledirectory.users.domain.UserProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
    @Mock private AddressRepository addresses;
    @Mock private UserProfileRepository profiles;
    @Mock private AdminAccountRepository admins;
    @Mock private AuditService audit;
    @InjectMocks private AddressService service;

    @Test
    void promotesNewPrimaryOnlyAfterExistingPrimaryIsUnset() {
        UserProfile user = new UserProfile("maya@example.test", "Maya", "Chen");
        Address previousPrimary = new Address(user, "Office", "1 Market St", null, "San Francisco", "CA", "94105", "US", true, 0);
        AdminAccount actor = new AdminAccount("admin@example.test", "hash", "Admin");
        when(profiles.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));
        when(addresses.findByUserProfileIdAndPrimaryTrueAndDeletedAtIsNull(user.getId())).thenReturn(List.of(previousPrimary));
        when(addresses.saveAndFlush(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(admins.findById(actor.getId())).thenReturn(Optional.of(actor));

        AddressResponse created = service.create(user.getId(),
                new AddressRequest("Home", "2 Oak Lane", null, "Oakland", "CA", "94610", "us", true, null), actor.getId());

        assertThat(previousPrimary.isPrimary()).isFalse();
        verify(addresses).flush();
        assertThat(created.countryCode()).isEqualTo("US");
        assertThat(created.displayOrder()).isZero();
        assertThat(created.primary()).isTrue();
        verify(audit).record(any(), org.mockito.ArgumentMatchers.eq("ADDRESS_PRIMARY_CLEARED"), org.mockito.ArgumentMatchers.eq("ADDRESS"),
                org.mockito.ArgumentMatchers.eq(previousPrimary.getId()), any());
        verify(audit).record(any(), org.mockito.ArgumentMatchers.eq("ADDRESS_CREATED"), org.mockito.ArgumentMatchers.eq("ADDRESS"), any(), any());
    }
}
