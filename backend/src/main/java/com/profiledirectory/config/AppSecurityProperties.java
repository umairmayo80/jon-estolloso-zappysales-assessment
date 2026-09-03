package com.profiledirectory.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Application-owned security settings. Secrets are intentionally supplied through environment
 * variables or mounted files; no production secret is committed to the repository.
 */
@ConfigurationProperties(prefix = "app")
public class AppSecurityProperties {

    private final Cookie cookie = new Cookie();
    private final Jwt jwt = new Jwt();
    private final RefreshToken refreshToken = new RefreshToken();
    private final Bootstrap bootstrap = new Bootstrap();
    private final Cors cors = new Cors();
    private final RateLimit rateLimit = new RateLimit();
    private final Request request = new Request();
    private boolean seedDemoData;

    public Cookie getCookie() { return cookie; }
    public Jwt getJwt() { return jwt; }
    public RefreshToken getRefreshToken() { return refreshToken; }
    public Bootstrap getBootstrap() { return bootstrap; }
    public Cors getCors() { return cors; }
    public RateLimit getRateLimit() { return rateLimit; }
    public Request getRequest() { return request; }
    public boolean isSeedDemoData() { return seedDemoData; }
    public void setSeedDemoData(boolean seedDemoData) { this.seedDemoData = seedDemoData; }

    public static class Cookie {
        private boolean secure;
        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }
    }

    public static class Jwt {
        private String issuer = "profile-directory-api";
        private String privateKeyPath = "";
        private String publicKeyPath = "";
        private Duration accessTtl = Duration.ofMinutes(15);
        private boolean requireKeyMaterial;

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getPrivateKeyPath() { return privateKeyPath; }
        public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }
        public String getPublicKeyPath() { return publicKeyPath; }
        public void setPublicKeyPath(String publicKeyPath) { this.publicKeyPath = publicKeyPath; }
        public Duration getAccessTtl() { return accessTtl; }
        public void setAccessTtl(Duration accessTtl) { this.accessTtl = accessTtl; }
        public boolean isRequireKeyMaterial() { return requireKeyMaterial; }
        public void setRequireKeyMaterial(boolean requireKeyMaterial) { this.requireKeyMaterial = requireKeyMaterial; }
    }

    public static class RefreshToken {
        private String pepper = "development-only-refresh-token-pepper-change-me";
        private Duration slidingTtl = Duration.ofDays(7);
        private Duration absoluteTtl = Duration.ofDays(30);

        public String getPepper() { return pepper; }
        public void setPepper(String pepper) { this.pepper = pepper; }
        public Duration getSlidingTtl() { return slidingTtl; }
        public void setSlidingTtl(Duration slidingTtl) { this.slidingTtl = slidingTtl; }
        public Duration getAbsoluteTtl() { return absoluteTtl; }
        public void setAbsoluteTtl(Duration absoluteTtl) { this.absoluteTtl = absoluteTtl; }
    }

    public static class Bootstrap {
        private boolean enabled;
        private String adminEmail = "";
        private String adminPassword = "";
        private String adminDisplayName = "Directory Administrator";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAdminEmail() { return adminEmail; }
        public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
        public String getAdminPassword() { return adminPassword; }
        public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
        public String getAdminDisplayName() { return adminDisplayName; }
        public void setAdminDisplayName(String adminDisplayName) { this.adminDisplayName = adminDisplayName; }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class RateLimit {
        private int authRequests = 10;
        private Duration authWindow = Duration.ofMinutes(1);
        public int getAuthRequests() { return authRequests; }
        public void setAuthRequests(int authRequests) { this.authRequests = authRequests; }
        public Duration getAuthWindow() { return authWindow; }
        public void setAuthWindow(Duration authWindow) { this.authWindow = authWindow; }
    }

    public static class Request {
        private DataSize maxBodySize = DataSize.ofMegabytes(1);

        public DataSize getMaxBodySize() { return maxBodySize; }
        public void setMaxBodySize(DataSize maxBodySize) { this.maxBodySize = maxBodySize; }
    }
}
