package com.profiledirectory.config;

import com.profiledirectory.auth.application.AdminPrincipal;
import com.profiledirectory.auth.application.AuthCookieService;
import com.profiledirectory.auth.application.CookieReader;
import com.profiledirectory.auth.application.JwtService;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CookieAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AdminAccountRepository admins;
    private final AuthCookieService cookies;

    public CookieAuthenticationFilter(JwtService jwt, AdminAccountRepository admins, AuthCookieService cookies) {
        this.jwt = jwt;
        this.admins = admins;
        this.cookies = cookies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawToken = CookieReader.get(request, AuthCookieService.ACCESS_COOKIE);
        if (rawToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwt.validate(rawToken).flatMap(subject -> admins.findById(subject.adminId()))
                    .filter(account -> account.isActive() && "ADMIN".equals(account.getRole().name()))
                    .ifPresentOrElse(account -> {
                        AdminPrincipal principal = AdminPrincipal.from(account);
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }, () -> cookies.clearAccess(response));
        }
        chain.doFilter(request, response);
    }
}
