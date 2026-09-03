package com.profiledirectory.config;

import com.profiledirectory.auth.application.AuthCookieService;
import com.profiledirectory.auth.application.JwtService;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieAuthenticationFilter cookieAuthenticationFilter,
            SecurityProblemWriter problemWriter,
            AppSecurityProperties properties) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieName("XSRF-TOKEN");
        csrf.setHeaderName("X-XSRF-TOKEN");
        csrf.setCookiePath("/");
        csrf.setCookieCustomizer(builder -> builder.sameSite("Strict").secure(properties.getCookie().isSecure()).path("/"));

        http
                .csrf(configurer -> configurer
                        .csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .cors(Customizer.withDefaults())
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(configurer -> configurer.disable())
                .formLogin(configurer -> configurer.disable())
                .httpBasic(configurer -> configurer.disable())
                .logout(configurer -> configurer.disable())
                .exceptionHandling(configurer -> configurer
                        .authenticationEntryPoint((request, response, exception) ->
                                problemWriter.write(request, response, org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> {
                            boolean csrfFailure = exception instanceof org.springframework.security.web.csrf.CsrfException;
                            problemWriter.write(request, response, org.springframework.http.HttpStatus.FORBIDDEN,
                                    csrfFailure ? "CSRF_INVALID" : "FORBIDDEN",
                                    csrfFailure ? "A valid CSRF token is required" : "You are not allowed to perform this action");
                        }))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // The reverse proxy exposes no actuator routes in production; CI and orchestrators
                        // need this narrow readiness endpoint without a browser authentication cookie.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/metrics", "/actuator/metrics/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; base-uri 'self'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31536000)));

        http.addFilterBefore(new ApiRequestSizeFilter(properties, problemWriter), CsrfFilter.class);
        http.addFilterBefore(new AuthRateLimitFilter(properties, problemWriter), UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(cookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CookieAuthenticationFilter cookieAuthenticationFilter(
            JwtService jwt, AdminAccountRepository admins, AuthCookieService cookies) {
        return new CookieAuthenticationFilter(jwt, admins, cookies);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppSecurityProperties properties) {
        List<String> allowedOrigins = properties.getCors().getAllowedOrigins().stream()
                .filter(StringUtils::hasText)
                .toList();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Same-origin production does not need CORS. Leaving the list empty rejects cross-origin calls.
        if (allowedOrigins.isEmpty()) {
            return source;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "If-Match", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("ETag", "X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
