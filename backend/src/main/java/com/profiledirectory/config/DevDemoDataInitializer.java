package com.profiledirectory.config;

import com.profiledirectory.addresses.domain.Address;
import com.profiledirectory.addresses.domain.AddressRepository;
import com.profiledirectory.users.domain.UserProfile;
import com.profiledirectory.users.domain.UserProfileRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deliberately small, idempotent data set for local UI development only. */
@Component
@Profile("dev")
@Order(2)
public class DevDemoDataInitializer implements ApplicationRunner {
    private final AppSecurityProperties properties;
    private final UserProfileRepository profiles;
    private final AddressRepository addresses;

    public DevDemoDataInitializer(
            AppSecurityProperties properties, UserProfileRepository profiles, AddressRepository addresses) {
        this.properties = properties;
        this.profiles = profiles;
        this.addresses = addresses;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isSeedDemoData() || profiles.count() > 0) {
            return;
        }
        UserProfile maya = profiles.save(new UserProfile("maya.chen@example.test", "Maya", "Chen"));
        UserProfile omar = profiles.save(new UserProfile("omar.hassan@example.test", "Omar", "Hassan"));
        UserProfile priya = profiles.save(new UserProfile("priya.shah@example.test", "Priya", "Shah"));
        addresses.save(new Address(maya, "Office", "410 Market Street", "Suite 900", "San Francisco", "CA", "94105", "US", true, 0));
        addresses.save(new Address(maya, "Home", "44 Oak Avenue", null, "Oakland", "CA", "94610", "US", false, 1));
        addresses.save(new Address(omar, "Home", "18 Garden Road", null, "Austin", "TX", "78701", "US", true, 0));
        addresses.save(new Address(priya, "Office", "12 Finsbury Square", null, "London", null, "EC2A 1AF", "GB", true, 0));
    }
}
