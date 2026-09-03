package com.profiledirectory.shared.web;

/** Request-scoped metadata used by error and audit responses without leaking servlet APIs. */
public final class RequestContext {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) { REQUEST_ID.set(requestId); }
    public static String requestId() {
        String requestId = REQUEST_ID.get();
        return requestId == null ? "unknown" : requestId;
    }
    public static void clear() { REQUEST_ID.remove(); }
}
