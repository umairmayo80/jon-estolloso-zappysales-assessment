package com.profiledirectory.addresses.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    boolean existsByUserProfileIdAndLabel(UUID userProfileId, String label);

    List<Address> findByUserProfileIdAndDeletedAtIsNullOrderByDisplayOrderAscCreatedAtAsc(UUID userProfileId);

    @Query("""
            select address from Address address
            where address.userProfile.id = :userProfileId
            order by case when address.deletedAt is null then 0 else 1 end, address.displayOrder asc, address.createdAt asc
            """)
    List<Address> findAllForUserOrdered(@Param("userProfileId") UUID userProfileId);

    Optional<Address> findByIdAndUserProfileId(UUID id, UUID userProfileId);
    long countByUserProfileIdAndDeletedAtIsNull(UUID userProfileId);
    List<Address> findByUserProfileIdAndPrimaryTrueAndDeletedAtIsNull(UUID userProfileId);
}
