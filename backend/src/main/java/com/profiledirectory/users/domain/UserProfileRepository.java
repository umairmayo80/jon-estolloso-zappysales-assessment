package com.profiledirectory.users.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    boolean existsByEmail(String email);

    @Query("""
            select u from UserProfile u
            where (:status = 'all'
                or (:status = 'active' and u.deletedAt is null)
                or (:status = 'deleted' and u.deletedAt is not null))
              and (:query = ''
                or lower(u.email) like lower(concat('%', :query, '%'))
                or lower(u.firstName) like lower(concat('%', :query, '%'))
                or lower(u.lastName) like lower(concat('%', :query, '%')))
            """)
    Page<UserProfile> search(@Param("query") String query, @Param("status") String status, Pageable pageable);

    Optional<UserProfile> findByIdAndDeletedAtIsNull(UUID id);
}
