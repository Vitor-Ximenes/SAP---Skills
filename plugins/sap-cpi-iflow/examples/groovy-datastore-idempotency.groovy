import com.sap.gateway.ip.core.customdev.util.Message
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Idempotency / duplicate detection keyed by a business key.
 *
 * WHAT IT SOLVES
 * A message that is delivered twice (JMS redelivery, a manual replay, a sender that retries after a
 * timeout) must not create a second order, invoice or payment. Duplicate detection needs two things:
 * a STABLE business key and a place to remember that the key was already processed.
 *
 * WHEN TO USE IT
 * In every flow whose last step has a side effect on the receiver side and whose sender can retry.
 *
 * ---------------------------------------------------------------------------------------------
 * IMPORTANT - WHAT THIS SCRIPT DOES NOT DO, AND WHY
 * This script does NOT call a Data Store API. The Data Store is accessed through the dedicated
 * iFlow steps (Data Store Select / Data Store Write), which are visible in the model, transactional
 * with the rest of the flow and supported by SAP. No Groovy Data Store client API is assumed here,
 * because inventing method names would produce code that compiles in a repository and fails in a
 * tenant.
 * The script therefore implements the half that a step cannot do - key normalisation, hashing,
 * decision making and observability - and communicates with the flow through exchange properties.
 * ---------------------------------------------------------------------------------------------
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   [Sender] -> SCRIPT A: extract the business key into p_keyPart1..p_keyPartN   (optional)
 *            -> THIS SCRIPT         : builds p_idempotencyKey, publishes the decision contract
 *            -> [Data Store Select] : Entry ID = ${property.p_idempotencyKey}
 *                                     "Throw Exception if Not Found" = false
 *            -> Content Modifier    : p_dsDuplicateFound = <whatever the Select step signalled, e.g.
 *                                     'true' when the entry was returned>          (see note below)
 *            -> THIS SCRIPT again   : now interprets p_dsDuplicateFound -> p_duplicateAction
 *            -> Router on ${property.p_duplicateAction}
 *                 PROCESS / PROCESS_DUPLICATE -> receiver call
 *                 SKIP_DUPLICATE              -> End (nothing to do)
 *                 REVIEW                      -> manual/DLQ handling
 *            -> [Data Store Write]  : Entry ID = ${property.p_idempotencyKey}, BEFORE the receiver call
 *                                     for PREVENT, or AFTER it for DETECT.
 *
 * The script is written to be safe to run once (before the Select, to build the key) and a second
 * time (after it, to interpret the result): every input is optional and no state is kept.
 *
 * The Data Store write step must store a SMALL entry. Data Store steps do not support streaming, so
 * store the key plus metadata (timestamp, interface, payload checksum) - not a multi-megabyte payload.
 *
 * ---------------------------------------------------------------------------------------------
 * DETECT vs PREVENT - choose deliberately
 *   DETECT  : the side effect happens first, the key is written afterwards, duplicates are only
 *             REPORTED (alerting, replay analysis, reconciliation reports). Safe to add to a running
 *             flow, does not change delivery semantics, cannot lose a message.
 *   PREVENT : the key is written BEFORE the side effect, so a duplicate never reaches the receiver.
 *             This only holds if the write and the effect are in the same transaction boundary - pair
 *             it with a JMS sender and an `Error End` in the Exception Subprocess so that a failed
 *             attempt rolls the marker back instead of blocking a message that was never processed.
 * ---------------------------------------------------------------------------------------------
 */
def Message processData(Message message) {
    // Step 1: derive the key. Stable inputs only - nothing that changes between two attempts.
    String key = buildKey(message)
    if (key == null) {
        message.setProperty("p_duplicateAction", "REVIEW")
        message.setProperty("p_duplicateFound", "false")
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "IDEMPOTENCY_KEY_MISSING")
        def messageLog = messageLogFactory.getMessageLog(message)
        if (messageLog != null) {
            messageLog.addCustomHeaderProperty("IdempotencyKeyState", "MISSING")
        }
        return message
    }

    message.setProperty("p_idempotencyKey", key)
    message.setProperty("p_idempotencyKeyShort", truncate(key, 16))

    // Step 2: interpret what the Data Store Select step found. Absent property = first pass.
    boolean duplicate = "true".equalsIgnoreCase(str(message.getProperty("p_dsDuplicateFound")) ?: "false")
    String mode = (str(message.getProperty("p_idempotencyMode")) ?: "PREVENT").toUpperCase()
    if (mode != "DETECT" && mode != "PREVENT") {
        // Refuse to guess: an unknown mode must not silently disable duplicate protection.
        throw new IllegalArgumentException("p_idempotencyMode must be DETECT or PREVENT, got: " + mode)
    }

    String action
    if (!duplicate) {
        action = "PROCESS"
    } else {
        action = (mode == "DETECT") ? "PROCESS_DUPLICATE" : "SKIP_DUPLICATE"
    }

    message.setProperty("p_duplicateFound", String.valueOf(duplicate))
    message.setProperty("p_idempotencyMode", mode)
    message.setProperty("p_duplicateAction", action)

    String status
    if (!duplicate) {
        status = "NEW_MESSAGE"
    } else {
        status = (action == "SKIP_DUPLICATE") ? "DUPLICATE_SKIPPED" : "DUPLICATE_DETECTED"
    }
    message.setProperty("SAP_MessageProcessingLogCustomStatus", truncate(status, 40))

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        // Searchable in Monitor: support can find every message that belongs to one business key.
        messageLog.addCustomHeaderProperty("IdempotencyKey", truncate(key, 120))
        messageLog.addCustomHeaderProperty("DuplicateAction", action)
        messageLog.addCustomHeaderProperty("IdempotencyMode", mode)
        String businessKey = str(message.getProperty("p_businessKey"))
        if (businessKey) {
            messageLog.addCustomHeaderProperty("BusinessKey", truncate(businessKey, 120))
        }
        if (duplicate && mode == "PREVENT") {
            messageLog.addAttachmentAsString("DuplicateSuppressed",
                    "action=" + action + "\nmode=" + mode +
                            "\nkey=" + key +
                            "\nsource=Data Store entry found for this key" +
                            "\nnote=Inspect the Data Store entry for the timestamp of the first processing.", "text/plain")
        }
    }
    return message
}

String buildKey(Message message) {
    // Preferred contract: contiguous business key parts supplied by the flow.
    StringBuilder raw = new StringBuilder()
    int index = 1
    while (index <= 10) {
        String part = str(message.getProperty("p_keyPart" + index))
        if (part == null) {
            break
        }
        raw.append(normalise(part)).append('|')
        index++
    }
    // Convenience contract: a single business key.
    if (raw.length() == 0) {
        String businessKey = str(message.getProperty("p_businessKey"))
        if (businessKey != null) {
            raw.append(normalise(businessKey))
        }
    }
    // Convenience contract: take the key from a named header of the incoming message.
    if (raw.length() == 0) {
        String headerName = str(message.getProperty("p_businessKeyHeaderName"))
        if (headerName != null) {
            String headerValue = str(message.getHeader(headerName, String.class))
            if (headerValue != null) {
                raw.append(normalise(headerValue))
            }
        }
    }
    if (raw.length() == 0) {
        return null
    }
    // Hashing gives a fixed-length, filesystem/HTTP/Data-Store-safe Entry ID and keeps business
    // values out of the key on systems where keys are visible to operators.
    return sha256Hex(raw.toString())
}

String normalise(String value) {
    // Case, surrounding blanks and repeated internal blanks must not create a different key for the
    // same business object; leading zeros are significant and are therefore preserved.
    return value.trim().replaceAll("\\s+", " ").toUpperCase()
}

String sha256Hex(String value) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256")
    byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8))
    StringBuilder hex = new StringBuilder(hash.length * 2)
    for (byte b : hash) {
        int v = b & 0xFF
        if (v < 16) {
            hex.append('0')
        }
        hex.append(Integer.toHexString(v))
    }
    return hex.toString()
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
