package com.profiledirectory.auth.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.config.AppSecurityProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtService {
    private final AppSecurityProperties properties;
    private final ResourceLoader resourceLoader;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    public JwtService(AppSecurityProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void initializeKeys() {
        boolean hasPrivate = StringUtils.hasText(properties.getJwt().getPrivateKeyPath());
        boolean hasPublic = StringUtils.hasText(properties.getJwt().getPublicKeyPath());
        if (hasPrivate != hasPublic) {
            throw new IllegalStateException("Both app.jwt.private-key-path and app.jwt.public-key-path are required together");
        }
        if (!hasPrivate) {
            if (properties.getJwt().isRequireKeyMaterial()) {
                throw new IllegalStateException("JWT key material is required in this profile");
            }
            generateEphemeralKeys();
            return;
        }
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(readPem(properties.getJwt().getPrivateKeyPath())));
            publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(readPem(properties.getJwt().getPublicKeyPath())));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load RSA JWT key material", exception);
        }
    }

    public String issue(AdminAccount account) {
        Instant now = Instant.now();
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getJwt().getIssuer())
                    .subject(account.getId().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(properties.getJwt().getAccessTtl())))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("role", account.getRole().name())
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("profile-directory-rs256").build(), claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
    }

    public Optional<JwtSubject> validate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new RSASSAVerifier(publicKey))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expires = claims.getExpirationTime();
            if (!properties.getJwt().getIssuer().equals(claims.getIssuer()) || expires == null || !expires.toInstant().isAfter(Instant.now())) {
                return Optional.empty();
            }
            String role = claims.getStringClaim("role");
            if (!"ADMIN".equals(role)) {
                return Optional.empty();
            }
            return Optional.of(new JwtSubject(UUID.fromString(claims.getSubject()), role));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private byte[] readPem(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("JWT key resource does not exist: " + location);
        }
        String pem;
        try (InputStream input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String encoded = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(encoded);
    }

    private void generateEphemeralKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            KeyPair pair = generator.generateKeyPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate development JWT key material", exception);
        }
    }

    public record JwtSubject(UUID adminId, String role) {
    }
}
