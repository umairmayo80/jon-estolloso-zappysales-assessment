package com.profiledirectory.addresses.application;

import com.profiledirectory.addresses.api.AddressRequest;
import com.profiledirectory.addresses.api.AddressResponse;
import com.profiledirectory.addresses.domain.Address;
import com.profiledirectory.addresses.domain.AddressRepository;
import com.profiledirectory.audit.application.AuditService;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import com.profiledirectory.shared.error.ConflictException;
import com.profiledirectory.shared.error.NotFoundException;
import com.profiledirectory.shared.web.EntityTag;
import com.profiledirectory.shared.web.InputNormalizer;
import com.profiledirectory.users.domain.UserProfile;
import com.profiledirectory.users.domain.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {
    private final AddressRepository addresses;
    private final UserProfileRepository profiles;
    private final AdminAccountRepository admins;
    private final AuditService audit;

    public AddressService(
            AddressRepository addresses,
            UserProfileRepository profiles,
            AdminAccountRepository admins,
            AuditService audit) {
        this.addresses = addresses;
        this.profiles = profiles;
        this.admins = admins;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AddressResponse get(UUID userId, UUID addressId) {
        return AddressResponse.from(findAddress(userId, addressId));
    }

    @Transactional(readOnly = true)
    public String currentEtag(UUID userId, UUID addressId) {
        Address address = findAddress(userId, addressId);
        return EntityTag.forAddress(address.getId(), address.getVersion());
    }

    @Transactional
    public AddressResponse create(UUID userId, AddressRequest request, UUID actorId) {
        UserProfile profile = findActiveProfile(userId);
        AdminAccount actor = actor(actorId);
        int displayOrder = request.displayOrder() == null
                ? addresses.findByUserProfileIdAndDeletedAtIsNullOrderByDisplayOrderAscCreatedAtAsc(userId).size()
                : request.displayOrder();
        Address address = new Address(profile, InputNormalizer.required(request.label()), InputNormalizer.required(request.line1()),
                InputNormalizer.optional(request.line2()), InputNormalizer.required(request.city()), InputNormalizer.optional(request.region()),
                InputNormalizer.optional(request.postalCode()), InputNormalizer.countryCode(request.countryCode()), request.primary(), displayOrder);
        if (request.primary()) {
            clearOtherPrimaries(userId, address.getId(), actor);
        }
        Address saved = addresses.saveAndFlush(address);
        audit.record(actor, "ADDRESS_CREATED", "ADDRESS", saved.getId(), Map.of("userId", userId, "primary", saved.isPrimary()));
        return AddressResponse.from(saved);
    }

    @Transactional
    public AddressResponse update(UUID userId, UUID addressId, AddressRequest request, String ifMatch, UUID actorId) {
        findActiveProfile(userId);
        Address address = findAddress(userId, addressId);
        EntityTag.requireMatch(ifMatch, EntityTag.forAddress(address.getId(), address.getVersion()));
        if (address.isDeleted()) {
            throw new ConflictException("Restore a deleted address before modifying it");
        }
        AdminAccount actor = actor(actorId);
        if (request.primary()) {
            clearOtherPrimaries(userId, addressId, actor);
        }
        int displayOrder = request.displayOrder() == null ? address.getDisplayOrder() : request.displayOrder();
        address.update(InputNormalizer.required(request.label()), InputNormalizer.required(request.line1()), InputNormalizer.optional(request.line2()),
                InputNormalizer.required(request.city()), InputNormalizer.optional(request.region()), InputNormalizer.optional(request.postalCode()),
                InputNormalizer.countryCode(request.countryCode()), request.primary(), displayOrder);
        Address saved = addresses.saveAndFlush(address);
        audit.record(actor, "ADDRESS_UPDATED", "ADDRESS", saved.getId(), Map.of("userId", userId, "primary", saved.isPrimary()));
        return AddressResponse.from(saved);
    }

    @Transactional
    public String softDelete(UUID userId, UUID addressId, String ifMatch, UUID actorId) {
        findActiveProfile(userId);
        Address address = findAddress(userId, addressId);
        EntityTag.requireMatch(ifMatch, EntityTag.forAddress(address.getId(), address.getVersion()));
        if (!address.isDeleted()) {
            AdminAccount actor = actor(actorId);
            address.softDelete(actor, Instant.now());
            addresses.saveAndFlush(address);
            audit.record(actor, "ADDRESS_DELETED", "ADDRESS", address.getId(), Map.of("userId", userId));
        }
        return EntityTag.forAddress(address.getId(), address.getVersion());
    }

    @Transactional
    public String restore(UUID userId, UUID addressId, String ifMatch, UUID actorId) {
        findActiveProfile(userId);
        Address address = findAddress(userId, addressId);
        EntityTag.requireMatch(ifMatch, EntityTag.forAddress(address.getId(), address.getVersion()));
        if (!address.isDeleted()) {
            throw new ConflictException("Address is already active");
        }
        AdminAccount actor = actor(actorId);
        if (address.isPrimary()) {
            clearOtherPrimaries(userId, addressId, actor);
        }
        address.restore();
        addresses.saveAndFlush(address);
        audit.record(actor, "ADDRESS_RESTORED", "ADDRESS", address.getId(), Map.of("userId", userId));
        return EntityTag.forAddress(address.getId(), address.getVersion());
    }

    private void clearOtherPrimaries(UUID userId, UUID replacementAddressId, AdminAccount actor) {
        List<Address> currentPrimaries = addresses.findByUserProfileIdAndPrimaryTrueAndDeletedAtIsNull(userId);
        List<Address> demoted = new java.util.ArrayList<>();
        for (Address primary : currentPrimaries) {
            if (!primary.getId().equals(replacementAddressId)) {
                primary.unsetPrimary();
                demoted.add(primary);
            }
        }
        if (!demoted.isEmpty()) {
            addresses.flush();
            for (Address address : demoted) {
                audit.record(actor, "ADDRESS_PRIMARY_CLEARED", "ADDRESS", address.getId(),
                        Map.of("userId", userId, "replacementAddressId", replacementAddressId));
            }
        }
    }

    private Address findAddress(UUID userId, UUID addressId) {
        return addresses.findByIdAndUserProfileId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Address was not found"));
    }

    private UserProfile findActiveProfile(UUID userId) {
        return profiles.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("Active user was not found"));
    }

    private AdminAccount actor(UUID actorId) {
        return admins.findById(actorId).orElseThrow(() -> new NotFoundException("Administrator was not found"));
    }
}
