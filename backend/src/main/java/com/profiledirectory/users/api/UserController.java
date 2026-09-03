package com.profiledirectory.users.api;

import com.profiledirectory.auth.application.AdminPrincipal;
import com.profiledirectory.shared.web.EntityTag;
import com.profiledirectory.users.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "List user profiles")
    public PageResponse<UserSummary> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return users.list(query, status, page, size, sort);
    }

    @PostMapping
    @Operation(summary = "Create a managed user profile")
    public ResponseEntity<UserDetail> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        UserDetail detail = users.create(request, principal.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(EntityTag.forUser(detail.id(), detail.version()))
                .body(detail);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a user profile with active and archived addresses")
    public ResponseEntity<UserDetail> get(@PathVariable UUID userId) {
        UserDetail detail = users.get(userId);
        return ResponseEntity.ok().eTag(EntityTag.forUser(detail.id(), detail.version())).body(detail);
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Update a user profile using a strong ETag precondition")
    public ResponseEntity<UserDetail> update(
            @PathVariable UUID userId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        UserDetail detail = users.update(userId, request, ifMatch, principal.id());
        return ResponseEntity.ok().eTag(EntityTag.forUser(detail.id(), detail.version())).body(detail);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Soft-delete a user profile")
    public ResponseEntity<Void> delete(
            @PathVariable UUID userId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResponseEntity.noContent().eTag(users.softDelete(userId, ifMatch, principal.id())).build();
    }

    @PostMapping("/{userId}/restore")
    @Operation(summary = "Restore a soft-deleted user profile")
    public ResponseEntity<Void> restore(
            @PathVariable UUID userId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResponseEntity.noContent().eTag(users.restore(userId, ifMatch, principal.id())).build();
    }
}
