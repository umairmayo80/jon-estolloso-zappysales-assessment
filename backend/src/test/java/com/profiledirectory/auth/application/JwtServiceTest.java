package com.profiledirectory.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.config.AppSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class JwtServiceTest {
    private JwtService jwt;

    @BeforeEach
    void setUp() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.getJwt().setIssuer("test-issuer");
        jwt = new JwtService(properties, new DefaultResourceLoader());
        jwt.initializeKeys();
    }

    @Test
    void issuesAnRs256TokenWithoutAdministratorPiiClaims() throws Exception {
        AdminAccount administrator = new AdminAccount("admin@example.test", "hash", "Directory Administrator");

        String token = jwt.issue(administrator);

        assertThat(jwt.validate(token)).hasValueSatisfying(subject -> {
            assertThat(subject.adminId()).isEqualTo(administrator.getId());
            assertThat(subject.role()).isEqualTo("ADMIN");
        });
        var claims = SignedJWT.parse(token).getJWTClaimsSet().getClaims();
        assertThat(claims).doesNotContainKeys("email", "name");
        assertThat(claims).containsKeys("iss", "sub", "iat", "exp", "jti", "role");
    }
}
