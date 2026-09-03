package com.profiledirectory.config;

import com.profiledirectory.shared.web.RequestBodyTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces an API body limit even when the service is reached without Nginx.
 *
 * <p>The Content-Length check rejects normal browser requests before parsing. The stream wrapper
 * also counts chunked requests so a direct client cannot bypass the limit by omitting that header.
 * The reverse proxy keeps the matching 1 MB limit as the first protective boundary.</p>
 */
public final class ApiRequestSizeFilter extends OncePerRequestFilter {
    private final AppSecurityProperties properties;
    private final SecurityProblemWriter problems;

    public ApiRequestSizeFilter(AppSecurityProperties properties, SecurityProblemWriter problems) {
        this.properties = properties;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long maxBytes = properties.getRequest().getMaxBodySize().toBytes();
        if (request.getContentLengthLong() > maxBytes) {
            writeTooLarge(request, response);
            return;
        }
        try {
            chain.doFilter(new SizeLimitedRequest(request, maxBytes), response);
        } catch (RequestBodyTooLargeException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            writeTooLarge(request, response);
        }
    }

    private void writeTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        problems.write(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body is too large");
    }

    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream inputStream;
        private BufferedReader reader;

        private SizeLimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new CountingServletInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                String encoding = getCharacterEncoding();
                Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }
    }

    private static final class CountingServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long consumed;

        private CountingServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            count(value < 0 ? 0 : 1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            count(Math.max(read, 0));
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void count(long justRead) throws RequestBodyTooLargeException {
            consumed += justRead;
            if (consumed > maxBytes) {
                throw new RequestBodyTooLargeException();
            }
        }
    }
}
