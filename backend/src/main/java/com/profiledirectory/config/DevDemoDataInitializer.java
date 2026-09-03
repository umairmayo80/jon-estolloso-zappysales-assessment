package com.profiledirectory.config;

import com.profiledirectory.addresses.domain.Address;
import com.profiledirectory.addresses.domain.AddressRepository;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import com.profiledirectory.shared.web.InputNormalizer;
import com.profiledirectory.users.domain.UserProfile;
import com.profiledirectory.users.domain.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent, fictional data set for local UI development only.
 *
 * <p>Fixtures are identified by their stable {@code example.test} email and by address label within
 * that fixture profile. Existing records are deliberately left untouched so developers can safely
 * edit the demo data while working locally.</p>
 */
@Component
@Profile("dev")
@Order(2)
public class DevDemoDataInitializer implements ApplicationRunner {
    private static final int EXPECTED_PROFILE_COUNT = 60;
    private static final int EXPECTED_ARCHIVED_PROFILE_COUNT = 6;
    private static final int EXPECTED_ADDRESS_COUNT = 79;
    private static final int EXPECTED_ARCHIVED_ADDRESS_COUNT = 1;
    private static final Instant MAYA_ARCHIVED_ADDRESS_AT = Instant.parse("2025-02-14T10:15:30Z");
    private static final Instant ARCHIVED_PROFILE_AT = Instant.parse("2025-01-15T09:30:00Z");
    private static final List<ProfileFixture> DEMO_PROFILES = fixtures();

    static {
        verifyFixtureShape();
    }

    private final AppSecurityProperties properties;
    private final UserProfileRepository profiles;
    private final AddressRepository addresses;
    private final AdminAccountRepository admins;

    public DevDemoDataInitializer(
            AppSecurityProperties properties,
            UserProfileRepository profiles,
            AddressRepository addresses,
            AdminAccountRepository admins) {
        this.properties = properties;
        this.profiles = profiles;
        this.addresses = addresses;
        this.admins = admins;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isSeedDemoData()) {
            return;
        }

        AdminAccount archiveActor = bootstrapAdminOrNull();
        for (ProfileFixture fixture : DEMO_PROFILES) {
            UserProfile profile = findOrCreateProfile(fixture, archiveActor);
            for (AddressFixture addressFixture : fixture.addresses()) {
                findOrCreateAddress(profile, addressFixture, archiveActor);
            }
        }
    }

    private UserProfile findOrCreateProfile(ProfileFixture fixture, AdminAccount archiveActor) {
        return profiles.findByEmail(fixture.email()).orElseGet(() -> {
            UserProfile profile = new UserProfile(fixture.email(), fixture.firstName(), fixture.lastName());
            if (fixture.archivedAt() != null) {
                profile.softDelete(archiveActor, fixture.archivedAt());
            }
            return profiles.save(profile);
        });
    }

    private void findOrCreateAddress(UserProfile profile, AddressFixture fixture, AdminAccount archiveActor) {
        if (addresses.existsByUserProfileIdAndLabel(profile.getId(), fixture.label())) {
            return;
        }

        Address address = new Address(
                profile,
                fixture.label(),
                fixture.line1(),
                fixture.line2(),
                fixture.city(),
                fixture.region(),
                fixture.postalCode(),
                fixture.countryCode(),
                fixture.primary(),
                fixture.displayOrder());
        if (fixture.archivedAt() != null) {
            address.softDelete(archiveActor, fixture.archivedAt());
        }
        addresses.save(address);
    }

    private AdminAccount bootstrapAdminOrNull() {
        String email = InputNormalizer.email(properties.getBootstrap().getAdminEmail());
        if (email == null || email.isBlank()) {
            return null;
        }
        return admins.findByEmail(email).orElse(null);
    }

    private static List<ProfileFixture> fixtures() {
        return List.of(
                new ProfileFixture("maya.chen@example.test", "Maya", "Chen", null, List.of(
                        primary("Office", "410 Market Street", "Suite 900", "San Francisco", "CA", "94105", "US"),
                        secondary("Home", "44 Oak Avenue", null, "Oakland", "CA", "94610", "US"),
                        archivedSecondary("Travel", "210 Lake Street", "Apt 14", "San Francisco", "CA", "94118", "US", MAYA_ARCHIVED_ADDRESS_AT))),
                activeWithSecondary("omar.hassan@example.test", "Omar", "Hassan",
                        primary("Home", "18 Garden Road", null, "Austin", "TX", "78701", "US"),
                        secondary("Office", "600 Congress Avenue", "Floor 12", "Austin", "TX", "78701", "US")),
                activeWithSecondary("priya.shah@example.test", "Priya", "Shah",
                        primary("Home", "12 Finsbury Square", null, "London", null, "EC2A 1AF", "GB"),
                        secondary("Office", "25 Rivington Street", "Level 3", "London", null, "EC2A 3DY", "GB")),
                activeWithSecondary("lucas.bennett@example.test", "Lucas", "Bennett",
                        primary("Home", "88 Willow Lane", null, "Portland", "OR", "97205", "US"),
                        secondary("Office", "121 SW Morrison Street", "Suite 500", "Portland", "OR", "97204", "US")),
                activeWithSecondary("sofia.martinez@example.test", "Sofia", "Martinez",
                        primary("Home", "27 Cedar Drive", "Unit 8", "Denver", "CO", "80202", "US"),
                        secondary("Office", "1700 Lincoln Street", "Floor 9", "Denver", "CO", "80203", "US")),
                activeWithSecondary("ethan.walker@example.test", "Ethan", "Walker",
                        primary("Home", "61 Harbor View", null, "Seattle", "WA", "98101", "US"),
                        secondary("Office", "701 5th Avenue", "Suite 2400", "Seattle", "WA", "98104", "US")),
                activeWithSecondary("aisha.rahman@example.test", "Aisha", "Rahman",
                        primary("Home", "9 Birch Close", null, "Manchester", null, "M1 2WD", "GB"),
                        secondary("Office", "58 Princess Street", "Level 6", "Manchester", null, "M1 6HS", "GB")),
                activeWithSecondary("noah.williams@example.test", "Noah", "Williams",
                        primary("Home", "105 Maple Street", null, "Chicago", "IL", "60601", "US"),
                        secondary("Office", "233 N Michigan Avenue", "Suite 1800", "Chicago", "IL", "60601", "US")),
                activeWithSecondary("chloe.nguyen@example.test", "Chloe", "Nguyen",
                        primary("Home", "33 Fern Street", "Apt 4C", "Boston", "MA", "02108", "US"),
                        secondary("Office", "1 Federal Street", "Floor 15", "Boston", "MA", "02110", "US")),
                activeWithSecondary("daniel.kim@example.test", "Daniel", "Kim",
                        primary("Home", "72 Juniper Road", null, "Irvine", "CA", "92614", "US"),
                        secondary("Office", "17901 Von Karman Avenue", "Suite 600", "Irvine", "CA", "92614", "US")),
                activeWithSecondary("elena.rossi@example.test", "Elena", "Rossi",
                        primary("Home", "14 Via Bellini", null, "Milan", "MI", "20121", "IT"),
                        secondary("Office", "28 Corso Garibaldi", "Piano 4", "Milan", "MI", "20121", "IT")),
                activeWithSecondary("marcus.johnson@example.test", "Marcus", "Johnson",
                        primary("Home", "46 Pinecrest Way", null, "Atlanta", "GA", "30303", "US"),
                        secondary("Office", "1100 Peachtree Street", "Suite 2100", "Atlanta", "GA", "30309", "US")),
                activeWithSecondary("fatima.ali@example.test", "Fatima", "Ali",
                        primary("Home", "205 King Street West", "Unit 1206", "Toronto", "ON", "M5V 1J5", "CA"),
                        secondary("Office", "250 University Avenue", "Floor 11", "Toronto", "ON", "M5H 3E5", "CA")),
                activeWithSecondary("henry.wilson@example.test", "Henry", "Wilson",
                        primary("Home", "22 Meadowbrook Road", null, "Raleigh", "NC", "27601", "US"),
                        secondary("Office", "333 Fayetteville Street", "Suite 800", "Raleigh", "NC", "27601", "US")),
                activeWithSecondary("isla.patel@example.test", "Isla", "Patel",
                        primary("Home", "7 Rosewood Mews", null, "Bristol", null, "BS1 4QA", "GB"),
                        secondary("Office", "16 Queen Square", "Level 2", "Bristol", null, "BS1 4NT", "GB")),
                activeWithSecondary("gabriel.torres@example.test", "Gabriel", "Torres",
                        primary("Home", "310 Valencia Street", "Unit 12", "San Diego", "CA", "92101", "US"),
                        secondary("Office", "550 West C Street", "Suite 1300", "San Diego", "CA", "92101", "US")),
                activeWithSecondary("zoe.brooks@example.test", "Zoe", "Brooks",
                        primary("Home", "19 Ash Grove", null, "Nashville", "TN", "37203", "US"),
                        secondary("Office", "222 2nd Avenue South", "Floor 7", "Nashville", "TN", "37201", "US")),
                activeWithSecondary("julian.fischer@example.test", "Julian", "Fischer",
                        primary("Home", "41 Lindenstrasse", null, "Berlin", null, "10115", "DE"),
                        secondary("Office", "8 Invalidenstrasse", "Etage 5", "Berlin", null, "10115", "DE")),
                active("amara.okafor@example.test", "Amara", "Okafor",
                        primary("Home", "14 Maple Crest", null, "Columbus", "OH", "43215", "US")),
                active("theo.martin@example.test", "Theo", "Martin",
                        primary("Home", "89 Rue des Fleurs", "Appartement 6", "Lyon", null, "69002", "FR")),
                active("layla.morgan@example.test", "Layla", "Morgan",
                        primary("Home", "53 Highland Avenue", null, "Edinburgh", null, "EH3 5DA", "GB")),
                active("isaac.cohen@example.test", "Isaac", "Cohen",
                        primary("Home", "67 Orchard Street", "Apt 3B", "New York", "NY", "10002", "US")),
                active("nina.petrov@example.test", "Nina", "Petrov",
                        primary("Home", "8 Danube Lane", null, "Prague", null, "110 00", "CZ")),
                active("samira.khan@example.test", "Samira", "Khan",
                        primary("Home", "119 Spring Street", null, "Charlotte", "NC", "28202", "US")),
                active("owen.clark@example.test", "Owen", "Clark",
                        primary("Home", "24 Riverbank Road", null, "Dublin", null, "D02 YX20", "IE")),
                active("keira.doyle@example.test", "Keira", "Doyle",
                        primary("Home", "6 Hawthorn Court", null, "Glasgow", null, "G1 1XQ", "GB")),
                active("mateo.silva@example.test", "Mateo", "Silva",
                        primary("Home", "31 Avenida del Sol", "Piso 2", "Madrid", null, "28013", "ES")),
                active("hana.suzuki@example.test", "Hana", "Suzuki",
                        primary("Home", "2-14 Sakura Street", null, "Tokyo", null, "150-0002", "JP")),
                active("caleb.reed@example.test", "Caleb", "Reed",
                        primary("Home", "73 Walnut Street", null, "Philadelphia", "PA", "19103", "US")),
                active("aria.blake@example.test", "Aria", "Blake",
                        primary("Home", "15 Seaview Terrace", null, "Brighton", null, "BN1 1AL", "GB")),
                active("victor.huang@example.test", "Victor", "Huang",
                        primary("Home", "480 King Street", "Unit 905", "Vancouver", "BC", "V6B 1L6", "CA")),
                active("jasmine.cooper@example.test", "Jasmine", "Cooper",
                        primary("Home", "97 Lakeview Drive", null, "Minneapolis", "MN", "55401", "US")),
                active("felix.weber@example.test", "Felix", "Weber",
                        primary("Home", "23 Rosenweg", null, "Hamburg", null, "20095", "DE")),
                active("leila.ahmed@example.test", "Leila", "Ahmed",
                        primary("Home", "68 Palm Court", "Flat 9", "Birmingham", null, "B1 1TB", "GB")),
                active("connor.murphy@example.test", "Connor", "Murphy",
                        primary("Home", "11 Clover Lane", null, "Cork", null, "T12 N8Y2", "IE")),
                active("rina.das@example.test", "Rina", "Das",
                        primary("Home", "40 Parkside Road", null, "Jersey City", "NJ", "07302", "US")),
                active("adrian.flores@example.test", "Adrian", "Flores",
                        primary("Home", "126 Magnolia Avenue", null, "Phoenix", "AZ", "85004", "US")),
                active("bianca.russo@example.test", "Bianca", "Russo",
                        primary("Home", "55 Via Dante", "Scala B", "Rome", "RM", "00184", "IT")),
                active("malik.thompson@example.test", "Malik", "Thompson",
                        primary("Home", "201 Elm Street", null, "Detroit", "MI", "48226", "US")),
                active("eva.lindgren@example.test", "Eva", "Lindgren",
                        primary("Home", "17 Birchall Street", null, "Stockholm", null, "111 52", "SE")),
                active("jonah.price@example.test", "Jonah", "Price",
                        primary("Home", "83 Cherry Lane", null, "Kansas City", "MO", "64106", "US")),
                active("tara.singh@example.test", "Tara", "Singh",
                        primary("Home", "33 Queen Street", "Unit 404", "Ottawa", "ON", "K1P 5C9", "CA")),
                active("leo.grant@example.test", "Leo", "Grant",
                        primary("Home", "72 Station Road", null, "Cambridge", null, "CB1 2JH", "GB")),
                active("mira.kapoor@example.test", "Mira", "Kapoor",
                        primary("Home", "9 Silver Oak Drive", null, "Dallas", "TX", "75201", "US")),
                active("dylan.wright@example.test", "Dylan", "Wright",
                        primary("Home", "50 Prospect Street", null, "Providence", "RI", "02903", "US")),
                active("yara.haddad@example.test", "Yara", "Haddad",
                        primary("Home", "76 Jasmine Road", "Apt 11", "Amman", null, "11181", "JO")),
                active("colin.hayes@example.test", "Colin", "Hayes",
                        primary("Home", "14 Brookfield Avenue", null, "Richmond", "VA", "23219", "US")),
                active("sienna.bell@example.test", "Sienna", "Bell",
                        primary("Home", "29 Harbor Lane", null, "Portsmouth", "NH", "03801", "US")),
                active("rafael.costa@example.test", "Rafael", "Costa",
                        primary("Home", "84 Rua das Flores", "Andar 3", "Lisbon", null, "1100-195", "PT")),
                active("anika.mehta@example.test", "Anika", "Mehta",
                        primary("Home", "36 Orchard Avenue", null, "San Jose", "CA", "95113", "US")),
                active("miles.turner@example.test", "Miles", "Turner",
                        primary("Home", "18 Kingsley Road", null, "Leeds", null, "LS1 4DY", "GB")),
                active("imani.scott@example.test", "Imani", "Scott",
                        primary("Home", "64 Poplar Street", null, "Baltimore", "MD", "21202", "US")),
                active("hugo.laurent@example.test", "Hugo", "Laurent",
                        primary("Home", "7 Rue Lafayette", null, "Paris", null, "75009", "FR")),
                active("freya.olsen@example.test", "Freya", "Olsen",
                        primary("Home", "102 Northgate Road", null, "Oslo", null, "0150", "NO")),
                archived("arthur.reed@example.test", "Arthur", "Reed",
                        primary("Home", "39 Spruce Street", null, "Madison", "WI", "53703", "US")),
                archived("helen.park@example.test", "Helen", "Park",
                        primary("Home", "91 Cypress Avenue", "Unit 6", "Los Angeles", "CA", "90012", "US")),
                archived("karim.saleh@example.test", "Karim", "Saleh",
                        primary("Home", "16 Olive Road", null, "Tampa", "FL", "33602", "US")),
                archived("beatrice.moore@example.test", "Beatrice", "Moore",
                        primary("Home", "74 Abbey Street", null, "Bath", null, "BA1 1NN", "GB")),
                archived("martin.novak@example.test", "Martin", "Novak",
                        primary("Home", "28 Castle Square", null, "Bratislava", null, "811 01", "SK")),
                archived("olivia.evans@example.test", "Olivia", "Evans",
                        primary("Home", "52 Willowbrook Lane", null, "Sacramento", "CA", "95814", "US")));
    }

    private static ProfileFixture active(
            String email, String firstName, String lastName, AddressFixture primaryAddress) {
        return new ProfileFixture(email, firstName, lastName, null, List.of(primaryAddress));
    }

    private static ProfileFixture activeWithSecondary(
            String email,
            String firstName,
            String lastName,
            AddressFixture primaryAddress,
            AddressFixture secondaryAddress) {
        return new ProfileFixture(email, firstName, lastName, null, List.of(primaryAddress, secondaryAddress));
    }

    private static ProfileFixture archived(
            String email, String firstName, String lastName, AddressFixture primaryAddress) {
        return new ProfileFixture(email, firstName, lastName, ARCHIVED_PROFILE_AT, List.of(primaryAddress));
    }

    private static AddressFixture primary(
            String label,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode) {
        return new AddressFixture(label, line1, line2, city, region, postalCode, countryCode, true, 0, null);
    }

    private static AddressFixture secondary(
            String label,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode) {
        return new AddressFixture(label, line1, line2, city, region, postalCode, countryCode, false, 1, null);
    }

    private static AddressFixture archivedSecondary(
            String label,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            Instant archivedAt) {
        return new AddressFixture(label, line1, line2, city, region, postalCode, countryCode, false, 2, archivedAt);
    }

    private static void verifyFixtureShape() {
        int profileCount = DEMO_PROFILES.size();
        long archivedProfileCount = DEMO_PROFILES.stream().filter(ProfileFixture::archived).count();
        int addressCount = DEMO_PROFILES.stream().mapToInt(fixture -> fixture.addresses().size()).sum();
        long archivedAddressCount = DEMO_PROFILES.stream()
                .flatMap(fixture -> fixture.addresses().stream())
                .filter(AddressFixture::archived)
                .count();
        long activePrimaryCount = DEMO_PROFILES.stream()
                .flatMap(fixture -> fixture.addresses().stream())
                .filter(address -> address.primary() && !address.archived())
                .count();

        if (profileCount != EXPECTED_PROFILE_COUNT
                || archivedProfileCount != EXPECTED_ARCHIVED_PROFILE_COUNT
                || addressCount != EXPECTED_ADDRESS_COUNT
                || archivedAddressCount != EXPECTED_ARCHIVED_ADDRESS_COUNT
                || activePrimaryCount != EXPECTED_PROFILE_COUNT) {
            throw new IllegalStateException("Development demo fixture counts are inconsistent");
        }
    }

    private record ProfileFixture(
            String email, String firstName, String lastName, Instant archivedAt, List<AddressFixture> addresses) {
        boolean archived() {
            return archivedAt != null;
        }
    }

    private record AddressFixture(
            String label,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            boolean primary,
            int displayOrder,
            Instant archivedAt) {
        boolean archived() {
            return archivedAt != null;
        }
    }
}
