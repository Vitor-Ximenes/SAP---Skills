import com.sap.gateway.ip.core.customdev.util.Message

/**
 * ============================================================================
 * CSRF token + session cookie handling for SAP Gateway / S/4HANA over plain HTTP
 * ============================================================================
 *
 * WHEN YOU NEED THIS
 *   The standard OData adapter manages CSRF protection by itself: if you can use it,
 *   do that and delete this script. You need this script only when you call a
 *   CSRF-protected service through a plain HTTP adapter (or a receiver that follows
 *   the same SAP Gateway convention).
 *
 * THE TWO-PHASE PATTERN
 *   Phase 1 (capture): a GET (or HEAD) request with the header `x-csrf-token: fetch`
 *                      must be sent first. The response carries the token and a
 *                      session cookie that belongs to that token.
 *   Phase 2 (apply):   the modifying request (POST / PUT / PATCH / DELETE) must carry
 *                      BOTH the token and the cookie: they are a pair. A token without
 *                      its cookie is rejected.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   1. CAL_FetchCsrfToken  — Request-Reply, HTTP GET to the service root,
 *                            header `x-csrf-token` = `fetch`.
 *   2. SCR_CsrfCapture     — THIS script with property `p_csrfMode` = `capture`
 *                            (a Content Modifier can set it before the call).
 *   3. ... the actual business request ...
 *   4. SCR_CsrfApply       — THIS script with property `p_csrfMode` = `apply`,
 *                            placed immediately before the modifying call, so the
 *                            headers are set on the outbound request.
 *
 * WHY THE TOKEN IS KEPT IN PROPERTIES
 *   Exchange properties stay inside the flow and are not leaked to other receivers.
 *   The headers are set only in the `apply` phase, on the request that actually
 *   needs them. The token and cookie are never logged.
 *
 * OPERATIONAL NOTE
 *   SAP sessions expire. A modifying call can fail because the token became invalid
 *   between the fetch and the call. Handle that case in the flow (a Router on the
 *   response code that returns to step 1 and retries once); do not loop indefinitely.
 */

def Message processData(Message message) {

    // "capture" (default) reads the token/cookie from the fetch response.
    // "apply" sets them on the outgoing modifying request.
    String mode = (message.getProperty("p_csrfMode") ?: "capture").toString().toLowerCase()

    if (mode == "apply") {
        return applyCsrfCredentials(message)
    }
    return captureCsrfCredentials(message)
}

// ---------------------------------------------------------------------------
// Phase 1: capture the token and the session cookie from the fetch response.
// ---------------------------------------------------------------------------
private Message captureCsrfCredentials(Message message) {

    // 1. The token. SAP Gateway returns it in the `x-csrf-token` response header.
    String token = message.getHeader("x-csrf-token", String.class)
    if (token == null || token.trim().isEmpty()) {
        // No token in the response: either the service is not CSRF protected, or the
        // fetch itself failed. Make it visible instead of continuing blindly.
        message.setProperty("p_csrfAvailable", "false")
        message.setProperty("p_errorSummary",
            "CSRF fetch did not return an x-csrf-token header. Verify the service root URL and the authentication.")
        return message
    }
    message.setProperty("p_csrfToken", token.trim())

    // 2. The session cookie. Camel may expose multiple Set-Cookie values as a List,
    //    an array, or a single String depending on the adapter and the response.
    String cookieHeader = extractCookieHeader(message.getHeader("Set-Cookie"))
    if (cookieHeader) {
        message.setProperty("p_csrfCookie", cookieHeader)
    }

    message.setProperty("p_csrfAvailable", "true")
    return message
}

// ---------------------------------------------------------------------------
// Phase 2: put the stored token and cookie on the outgoing request.
// ---------------------------------------------------------------------------
private Message applyCsrfCredentials(Message message) {

    String token = message.getProperty("p_csrfToken")
    String cookie = message.getProperty("p_csrfCookie")

    if (!token) {
        // Without a token the modifying call is guaranteed to fail with a 403.
        // Fail explicitly here: a clear error beats an obscure rejection later.
        throw new IllegalStateException(
            "No CSRF token available. Run the CSRF fetch step before this step.")
    }

    // These two headers are protocol metadata for this specific call, so headers are
    // the correct place for them (see the properties-vs-headers rule).
    message.setHeader("x-csrf-token", token)
    if (cookie) {
        message.setHeader("Cookie", cookie)
    }

    return message
}

// ---------------------------------------------------------------------------
// Normalises Set-Cookie into a single `name=value; name2=value2` header value.
// Attributes (Path, Expires, Secure, HttpOnly, SameSite, Domain) must be dropped:
// sending them back inside a Cookie header is invalid and some servers reject it.
// ---------------------------------------------------------------------------
private String extractCookieHeader(def setCookie) {

    if (setCookie == null) {
        return null
    }

    List<String> rawValues = []
    if (setCookie instanceof Collection) {
        setCookie.each { if (it != null) rawValues.add(it.toString()) }
    } else if (setCookie.getClass().isArray()) {
        setCookie.each { if (it != null) rawValues.add(it.toString()) }
    } else {
        rawValues.add(setCookie.toString())
    }

    List<String> pairs = []
    for (String value : rawValues) {
        // Each element may itself contain several cookies separated by a comma that is
        // not part of an Expires date. Splitting on ";" first is safe: the first
        // segment is always the name=value pair.
        for (String part : value.split(";")) {
            String trimmed = part.trim()
            if (trimmed && trimmed.contains("=") && !isCookieAttribute(trimmed)) {
                pairs.add(trimmed)
            }
        }
    }

    return pairs.isEmpty() ? null : pairs.join("; ")
}

private boolean isCookieAttribute(String candidate) {
    String name = candidate.substring(0, candidate.indexOf("=")).trim().toLowerCase()
    return name in ["path", "domain", "expires", "max-age", "secure", "httponly", "samesite", "version", "comment"]
}
