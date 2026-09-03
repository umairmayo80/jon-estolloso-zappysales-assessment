package com.profiledirectory.config;

import com.profiledirectory.config.SecurityProblemWriter;
import com.profiledirectory.config.AppSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Small in-process guard for password/refresh abuse; deploy a shared edge limiter for multi-node production. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AppSecurityProperties properties;
    private final SecurityProblemWriter problems;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(AppSecurityProperties properties, SecurityProblemWriter problems) {
        this.properties = properties;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !("/api/v1/auth/login".equals(path) || "/api/v1/auth/refresh".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = Instant.now();
        String key = request.getRemoteAddr() + ':' + request.getRequestURI();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        if (!window.tryAcquire(now, properties.getRateLimit().getAuthWindow(), properties.getRateLimit().getAuthRequests())) {
            long retryAfter = Math.max(1, window.secondsUntilReset(now, properties.getRateLimit().getAuthWindow()));
            response.setHeader("Retry-After", Long.toString(retryAfter));
            problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many authentication attempts; try again shortly");
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        private Instant started;
        private int count;

        private Window(Instant started) { this.started = started; }

        synchronized boolean tryAcquire(Instant now, java.time.Duration duration, int limit) {
            if (!now.isBefore(started.plus(duration))) {
                started = now;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }

        synchronized long secondsUntilReset(Instant now, java.time.Duration duration) {
            return java.time.Duration.between(now, started.plus(duration)).toSeconds();
        }
    }
}
