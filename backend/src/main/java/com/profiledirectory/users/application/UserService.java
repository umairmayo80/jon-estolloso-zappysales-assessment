package com.profiledirectory.users.application;

import com.profiledirectory.addresses.api.AddressResponse;
import com.profiledirectory.addresses.domain.AddressRepository;
import com.profiledirectory.audit.application.AuditService;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import com.profiledirectory.shared.error.ConflictException;
import com.profiledirectory.shared.error.InvalidRequestException;
import com.profiledirectory.shared.error.NotFoundException;
import com.profiledirectory.shared.web.EntityTag;
import com.profiledirectory.shared.web.InputNormalizer;
import com.profiledirectory.users.api.CreateUserRequest;
import com.profiledirectory.users.api.PageResponse;
import com.profiledirectory.users.api.UpdateUserRequest;
import com.profiledirectory.users.api.UserDetail;
import com.profiledirectory.users.api.UserSummary;
import com.profiledirectory.users.domain.UserProfile;
import com.profiledirectory.users.domain.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserProfileRepository profiles;
    private final AddressRepository addresses;
    private final AdminAccountRepository admins;
    private final AuditService audit;

    public UserService(
            UserProfileRepository profiles,
            AddressRepository addresses,
            AdminAccountRepository admins,
            AuditService audit) {
        this.profiles = profiles;
        this.addresses = addresses;
        this.admins = admins;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> list(String query, String status, int page, Integer size, String sort) {
        if (page < 0) {
            throw new InvalidRequestException("page must not be negative");
        }
        String normalizedQuery = InputNormalizer.optional(query);
        if (normalizedQuery != null && normalizedQuery.length() > 120) {
            throw new InvalidRequestException("query must be at most 120 characters");
        }
        int requestedSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (requestedSize < 1) {
            throw new InvalidRequestException("size must be at least 1");
        }
        int pageSize = Math.min(requestedSize, MAX_PAGE_SIZE);
        Sort resolvedSort = resolveSort(sort);
        String normalizedSort = toSortString(resolvedSort);
        UserStatus resolvedStatus = UserStatus.parse(status);
        Pageable pageable = PageRequest.of(page, pageSize, resolvedSort);
        Page<UserProfile> results = profiles.search(normalizedQuery == null ? "" : normalizedQuery, resolvedStatus.apiValue(), pageable);
        return PageResponse.from(results,
                profile -> UserSummary.from(profile, addresses.countByUserProfileIdAndDeletedAtIsNull(profile.getId())),
                normalizedSort);
    }

    @Transactional(readOnly = true)
    public UserDetail get(UUID userId) {
        UserProfile profile = findProfile(userId);
        List<AddressResponse> activeAddresses = addresses.findAllForUserOrdered(userId)
                .stream().map(AddressResponse::from).toList();
        return UserDetail.from(profile, activeAddresses);
    }

    @Transactional(readOnly = true)
    public String currentEtag(UUID userId) {
        UserProfile profile = findProfile(userId);
        return EntityTag.forUser(profile.getId(), profile.getVersion());
    }

    @Transactional
    public UserDetail create(CreateUserRequest request, UUID actorId) {
        String email = InputNormalizer.email(request.email());
        if (profiles.existsByEmail(email)) {
            throw new ConflictException("A user with that email already exists, including soft-deleted profiles");
        }
        UserProfile profile = profiles.saveAndFlush(new UserProfile(
                email, InputNormalizer.required(request.firstName()), InputNormalizer.required(request.lastName())));
        audit.record(actor(actorId), "USER_CREATED", "USER_PROFILE", profile.getId(), Map.of("fields", List.of("email", "firstName", "lastName")));
        return UserDetail.from(profile, List.of());
    }

    @Transactional
    public UserDetail update(UUID userId, UpdateUserRequest request, String ifMatch, UUID actorId) {
        UserProfile profile = findProfile(userId);
        EntityTag.requireMatch(ifMatch, EntityTag.forUser(profile.getId(), profile.getVersion()));
        if (profile.isDeleted()) {
            throw new ConflictException("Restore a deleted user before modifying it");
        }
        profile.update(
                InputNormalizer.email(request.email()),
                InputNormalizer.required(request.firstName()),
                InputNormalizer.required(request.lastName()));
        UserProfile saved = profiles.saveAndFlush(profile);
        audit.record(actor(actorId), "USER_UPDATED", "USER_PROFILE", saved.getId(), Map.of("fields", List.of("email", "firstName", "lastName")));
        return detailFor(saved);
    }

    @Transactional
    public String softDelete(UUID userId, String ifMatch, UUID actorId) {
        UserProfile profile = findProfile(userId);
        EntityTag.requireMatch(ifMatch, EntityTag.forUser(profile.getId(), profile.getVersion()));
        if (!profile.isDeleted()) {
            AdminAccount actor = actor(actorId);
            profile.softDelete(actor, Instant.now());
            profiles.saveAndFlush(profile);
            audit.record(actor, "USER_DELETED", "USER_PROFILE", profile.getId(), Map.of());
        }
        return EntityTag.forUser(profile.getId(), profile.getVersion());
    }

    @Transactional
    public String restore(UUID userId, String ifMatch, UUID actorId) {
        UserProfile profile = findProfile(userId);
        EntityTag.requireMatch(ifMatch, EntityTag.forUser(profile.getId(), profile.getVersion()));
        if (!profile.isDeleted()) {
            throw new ConflictException("User is already active");
        }
        profile.restore();
        profiles.saveAndFlush(profile);
        audit.record(actor(actorId), "USER_RESTORED", "USER_PROFILE", profile.getId(), Map.of());
        return EntityTag.forUser(profile.getId(), profile.getVersion());
    }

    private UserDetail detailFor(UserProfile profile) {
        List<AddressResponse> activeAddresses = addresses.findAllForUserOrdered(profile.getId())
                .stream().map(AddressResponse::from).toList();
        return UserDetail.from(profile, activeAddresses);
    }

    private UserProfile findProfile(UUID id) {
        return profiles.findById(id).orElseThrow(() -> new NotFoundException("User was not found"));
    }

    private AdminAccount actor(UUID actorId) {
        return admins.findById(actorId).orElseThrow(() -> new NotFoundException("Administrator was not found"));
    }

    private Sort resolveSort(String requested) {
        String value = requested == null || requested.isBlank() ? "lastName,asc" : requested.trim();
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw new InvalidRequestException("sort must use field,direction");
        }
        String property = switch (parts[0]) {
            case "firstName", "lastName", "email", "updatedAt", "createdAt" -> parts[0];
            default -> throw new InvalidRequestException("sort field is not supported");
        };
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("sort direction must be asc or desc");
        }
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private String toSortString(Sort sort) {
        Sort.Order first = sort.iterator().next();
        return first.getProperty() + "," + first.getDirection().name().toLowerCase();
    }
}
