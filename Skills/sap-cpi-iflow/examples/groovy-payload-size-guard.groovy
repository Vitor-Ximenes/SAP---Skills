import com.sap.gateway.ip.core.customdev.util.Message
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Guards the payload size before any expensive step, and rejects oversized messages explicitly.
 *
 * WHAT IT SOLVES
 * A single oversized message can exhaust the worker heap, fill the temporary storage used by
 * streaming steps, or time out every downstream call. The failure then looks like a mysterious
 * OutOfMemoryError far away from the message that caused it. This script measures the body while
 * reading it in bounded blocks, sets a searchable custom status, and either fails with a clear
 * exception or publishes a flag for a Router - without ever holding more than the configured limit
 * in heap.
 *
 * WHEN TO USE IT
 * - As the FIRST step after the sender adapter of every flow that accepts files or bulk payloads.
 * - When different limits apply per interface or per sender, so a fixed adapter setting is not enough.
 * The first line of defence remains the sender adapter: HTTP-based senders expose a Body Size
 * parameter (tab Conditions) that rejects oversized messages before any step runs. This script is the
 * second line, for adapters without that parameter and for business-specific limits.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   [Sender] -> THIS SCRIPT -> Router on ${property.p_payloadTooLarge}
 *                                |-- false -> normal processing
 *                                `-- true  -> reject branch: build an error response, End Message
 * Content Modifier before this script:
 *   p_maxPayloadBytes -> '{{max_payload_bytes}}'   hard limit in bytes (default in script: 10485760)
 *   p_warnPayloadBytes-> '{{warn_payload_bytes}}'  soft limit, only status/logging (optional)
 *   p_failOnOversize  -> 'true' | 'false'          true = throw (Exception Subprocess handles it)
 *                                                  false = flag only, the Router decides
 *
 * Exchange properties published:
 *   p_payloadBytes            measured size in bytes (of the accepted part when oversized)
 *   p_payloadKb                same value in KB, for logging keys
 *   p_payloadTooLarge         'true' | 'false'
 *   p_payloadWarning          'true' when the soft limit was exceeded
 *   p_contentLengthMismatch   'true' when a Content-Length header disagreed with the measured size
 *   SAP_MessageProcessingLogCustomStatus  PAYLOAD_OK | PAYLOAD_LARGE | PAYLOAD_TOO_LARGE
 *
 * NOTES
 * - `InputStream.available()` is NOT used: it is a hint about what can be read without blocking, not
 *   the size of the stream, and it is wrong for compressed or chunked transfers.
 * - A Content-Length header, when present, is only compared as a hint - some senders omit it, some
 *   send the size of the compressed form.
 * - Streaming steps store large payloads in the tenant's temporary storage (Operations -> Inspect
 *   Temporary Storage). Chunking upstream (see groovy-csv-stream-reader.groovy) is the alternative
 *   when a legitimate payload exceeds the temporary storage limit.
 */
def Message processData(Message message) {
    long maxBytes = parseLong(str(message.getProperty("p_maxPayloadBytes")), 10485760L)
    long warnBytes = parseLong(str(message.getProperty("p_warnPayloadBytes")), -1L)
    boolean failOnOversize = !"false".equalsIgnoreCase(str(message.getProperty("p_failOnOversize")) ?: "true")
    if (maxBytes < 1) {
        maxBytes = 1
    }

    InputStream body = message.getBody(java.io.InputStream)
    if (body == null) {
        message.setProperty("p_payloadBytes", "0")
        message.setProperty("p_payloadKb", "0")
        message.setProperty("p_payloadTooLarge", "false")
        message.setProperty("p_payloadWarning", "false")
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "PAYLOAD_EMPTY")
        return message
    }

    long hint = parseLong(str(message.getHeader("Content-Length", String.class)), -1L)

    // Initial capacity is a hint only: the buffer never grows past maxBytes + one read block,
    // because the loop stops at the first byte above the limit.
    ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) Math.min(maxBytes, 1048576L))
    long total = 0L
    boolean oversize = false
    byte[] block = new byte[8192]
    try {
        int read
        while ((read = body.read(block)) != -1) {
            total += read
            if (total > maxBytes) {
                oversize = true
                break   // stop reading: no further heap is allocated for this message
            }
            buffer.write(block, 0, read)
        }
    } finally {
        body.close()
    }

    long measured = oversize ? total : buffer.size()
    message.setProperty("p_payloadBytes", String.valueOf(measured))
    message.setProperty("p_payloadKb", String.valueOf(Math.round(measured / 1024.0)))
    if (hint > 0 && hint != measured) {
        // Not fatal, but a mismatch usually means a compressed transfer or a broken sender.
        message.setProperty("p_contentLengthMismatch", "true")
    }

    if (oversize) {
        message.setProperty("p_payloadTooLarge", "true")
        message.setProperty("p_payloadWarning", "true")
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "PAYLOAD_TOO_LARGE")

        def messageLog = messageLogFactory.getMessageLog(message)
        if (messageLog != null) {
            messageLog.addCustomHeaderProperty("PayloadBytes", String.valueOf(measured))
            messageLog.addCustomHeaderProperty("PayloadLimit", String.valueOf(maxBytes))
            messageLog.addAttachmentAsString("PayloadSizeReport",
                    "limitBytes=" + maxBytes + "\nbytesReadBeforeAbort=" + measured +
                            "\ncontentLengthHint=" + (hint > 0 ? hint : "<absent>") +
                            "\nnote=Reading stopped at the first byte above the limit; the body was NOT re-set, " +
                            "so route this message to the reject branch instead of processing it.", "text/plain")
        }
        if (failOnOversize) {
            // A plain JDK exception: the Exception Subprocess classifies and logs it like any other failure.
            throw new IllegalArgumentException(
                    "Payload exceeds the configured limit of " + maxBytes + " bytes (at least " + measured + " bytes were read)")
        }
        // Body deliberately not re-set: the payload was not fully read, so it cannot be handed on.
        return message
    }

    message.setProperty("p_payloadTooLarge", "false")
    boolean warning = (warnBytes > 0 && buffer.size() > warnBytes)
    message.setProperty("p_payloadWarning", String.valueOf(warning))
    message.setProperty("SAP_MessageProcessingLogCustomStatus", warning ? "PAYLOAD_LARGE" : "PAYLOAD_OK")

    // Hand the measured payload on instead of the consumed stream.
    message.setBody(buffer.toByteArray())

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("PayloadBytes", String.valueOf(buffer.size()))
        if (warning) {
            messageLog.addCustomHeaderProperty("PayloadWarnLimit", String.valueOf(warnBytes))
        }
    }
    return message
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
