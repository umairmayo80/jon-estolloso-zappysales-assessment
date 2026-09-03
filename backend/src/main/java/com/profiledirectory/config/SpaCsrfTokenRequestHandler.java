package com.profiledirectory.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.util.StringUtils;

/**
 * Forces CSRF token creation for the SPA and accepts the unmasked token copied from the
 * XSRF-TOKEN cookie into X-XSRF-TOKEN, while preserving Spring Security's form fallback.
 */
public final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
    private final CsrfTokenRequestHandler plainHandler = new CsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> deferredToken) {
        deferredToken.get();
        super.handle(request, response, deferredToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String header = request.getHeader(csrfToken.getHeaderName());
        return StringUtils.hasText(header)
                ? plainHandler.resolveCsrfTokenValue(request, csrfToken)
                : super.resolveCsrfTokenValue(request, csrfToken);
    }
}
