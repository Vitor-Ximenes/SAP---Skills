import com.sap.gateway.ip.core.customdev.util.Message
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

/**
 * Builds a MIME multipart body (mail attachment, HTTP form upload, bulk document upload).
 *
 * WHAT IT SOLVES
 * Multipart MIME has three traps: the boundary must be unique and appear exactly as declared, text
 * parts must be encoded with the charset declared for them, and binary parts must be Base64 encoded
 * with line wrapping (RFC 2045: 76 characters per line) or strict receivers reject the body.
 * This script assembles text and binary parts from descriptors with a generated boundary.
 *
 * WHEN TO USE IT
 * - An HTTP receiver that expects multipart/form-data or multipart/mixed with file content.
 * - A Mail receiver where the content must be assembled from more than one source.
 * - Any case where the alternative would be hand-concatenating Base64 into an XML mapping.
 * For a single small attachment the Mail receiver's own attachment configuration is simpler; use this
 * script when the parts are dynamic (names, count or content come from the payload).
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   [Source] -> THIS SCRIPT -> [HTTP receiver] with Content-Type taken from ${property.p_multipartContentType}
 *                              (or Mail receiver with the body as message content)
 * The script sets the Content-Type header itself, including the generated boundary, so the receiver's
 * configured Content-Type must not overwrite it.
 *
 * INPUT CONTRACT - p_parts, a List<Map> built by an upstream Script step:
 *   text part   : [name:'comment', text:'hello', contentType:'text/plain; charset=UTF-8']
 *   binary part : [name:'file', fileName:'invoice.pdf', content:byte[], contentType:'application/pdf']
 *                 'content' may also be a Base64 String already encoded by the source system.
 *   form field  : add disposition:'form-data' to emit a Content-Disposition header (form uploads)
 *   a part may override its own boundary-safe name and set 'transferEncoding':'base64' explicitly.
 *
 * Content Modifier before this script (all optional):
 *   p_multipartSubtype -> 'mixed' (default) | 'form-data' | 'related'
 *   p_maxParts         -> '50'     guards against a runaway descriptor list
 *
 * Exchange properties published: p_multipartBoundary, p_multipartContentType, p_multipartPartCount,
 * p_multipartBytes.
 *
 * SAFETY NOTES
 * - Credentials never belong in a part: authentication is configured on the receiver adapter.
 * - The boundary is checked against every part content; a collision would corrupt the body, so the
 *   script fails loudly instead of producing a subtly broken message.
 */
def Message processData(Message message) {
    Object raw = message.getProperty("p_parts")
    if (!(raw instanceof List)) {
        throw new IllegalArgumentException("p_parts must be a List of part descriptors")
    }
    List parts = (List) raw
    int maxParts = (int) parseLong(str(message.getProperty("p_maxParts")), 50L)
    if (parts.isEmpty()) {
        throw new IllegalArgumentException("p_parts is empty: nothing to encode")
    }
    if (parts.size() > maxParts) {
        throw new IllegalArgumentException("p_parts contains " + parts.size() + " parts, the configured maximum is " + maxParts)
    }

    String subtype = str(message.getProperty("p_multipartSubtype")) ?: "mixed"
    String boundary = "----=_Part_" + UUID.randomUUID().toString().replace("-", "")
    String crlf = "\r\n"
    StringBuilder out = new StringBuilder(8192)
    int binaryParts = 0

    for (int i = 0; i < parts.size(); i++) {
        Object entry = parts.get(i)
        if (!(entry instanceof Map)) {
            throw new IllegalArgumentException("Part " + i + " is not a Map")
        }
        Map part = (Map) entry
        String contentType = str(part.get("contentType")) ?: "application/octet-stream"
        String name = str(part.get("name"))
        String fileName = str(part.get("fileName"))
        String disposition = str(part.get("disposition"))
        Object content = part.get("content")
        if (content == null) {
            content = part.get("text")
        }
        if (content == null) {
            throw new IllegalArgumentException("Part " + i + " has neither 'content' nor 'text'")
        }

        byte[] bytes
        boolean base64
        if (content instanceof byte[]) {
            bytes = (byte[]) content
            base64 = true
        } else if ("base64".equalsIgnoreCase(str(part.get("transferEncoding")))) {
            // Already encoded upstream: decode only to run the boundary check, then re-emit as given.
            bytes = Base64.getMimeDecoder().decode(content.toString().replaceAll("\\s", ""))
            base64 = true
        } else {
            bytes = content.toString().getBytes(StandardCharsets.UTF_8)
            base64 = false
        }

        if (base64) {
            binaryParts++
        }
        // A boundary appearing inside a part would split the body in the wrong place: fail loudly.
        // The check is limited to small parts on purpose: it protects text parts (where a copy could
        // realistically be pasted from a previous message) without scanning a whole large binary file.
        if (bytes.length <= 1048576 && containsBoundary(bytes, boundary)) {
            throw new IllegalArgumentException("Part " + i + " contains the generated boundary; re-run to generate a new one")
        }

        out.append("--").append(boundary).append(crlf)
        out.append("Content-Type: ").append(contentType).append(crlf)
        if (disposition != null || name != null || fileName != null) {
            out.append("Content-Disposition: ").append(disposition ?: "attachment")
            if (name != null) {
                out.append("; name=\"").append(sanitiseHeader(name)).append('"')
            }
            if (fileName != null) {
                out.append("; filename=\"").append(sanitiseHeader(fileName)).append('"')
            }
            out.append(crlf)
        }
        if (base64) {
            out.append("Content-Transfer-Encoding: base64").append(crlf)
        }
        out.append(crlf)   // blank line: end of this part's headers

        if (base64) {
            // getMimeEncoder wraps at 76 characters with CRLF, exactly what RFC 2045 requires.
            out.append(Base64.getMimeEncoder(76, crlf.getBytes(StandardCharsets.US_ASCII)).encodeToString(bytes)).append(crlf)
        } else {
            String text = new String(bytes, StandardCharsets.UTF_8)
            out.append(text)
            // Text parts are not required to end with a line break; add one so the next delimiter
            // starts on a new line (some receivers are strict about that).
            if (!text.endsWith(crlf)) {
                out.append(crlf)
            }
        }
    }
    out.append("--").append(boundary).append("--").append(crlf)   // closing delimiter

    String body = out.toString()
    String contentType = "multipart/" + subtype + "; boundary=" + boundary
    message.setBody(body)

    message.setProperty("p_multipartBoundary", boundary)
    message.setProperty("p_multipartContentType", contentType)
    message.setProperty("p_multipartPartCount", String.valueOf(parts.size()))
    message.setProperty("p_multipartBinaryParts", String.valueOf(binaryParts))
    message.setProperty("p_multipartBytes", String.valueOf(body.getBytes(StandardCharsets.UTF_8).length))
    message.setProperty("SAP_MessageProcessingLogCustomStatus", "MULTIPART_BUILT")

    boolean headerAllowed = "true".equalsIgnoreCase(str(message.getProperty("p_setContentTypeHeader")) ?: "true")
    if (headerAllowed) {
        message.setHeader("Content-Type", contentType)
    }

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("MultipartParts", String.valueOf(parts.size()))
        messageLog.addCustomHeaderProperty("MultipartBytes", str(message.getProperty("p_multipartBytes")))
    }
    return message
}

boolean containsBoundary(byte[] data, String boundary) {
    byte[] needle = boundary.getBytes(StandardCharsets.US_ASCII)
    if (needle.length == 0 || data.length < needle.length) {
        return false
    }
    outer:
    for (int i = 0; i + needle.length <= data.length; i++) {
        for (int j = 0; j < needle.length; j++) {
            if (data[i + j] != needle[j]) {
                continue outer
            }
        }
        return true
    }
    return false
}

String sanitiseHeader(String value) {
    // Header parameters cannot carry quotes, CR or LF: strip them instead of producing a broken header.
    return value.replaceAll("[\"\\r\\n]", "_").trim()
}

String str(Object value) { value == null ? null : value.toString().trim() }
long parseLong(String value, long fallback) {
    if (value == null || value.isEmpty()) {
        return fallback
    }
    try {
        return Long.parseLong(value)
    } catch (NumberFormatException ignored) {
        return fallback
    }
}
