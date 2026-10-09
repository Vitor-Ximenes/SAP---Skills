import com.sap.gateway.ip.core.customdev.util.Message

/**
 * ============================================================================
 * MPL observability: custom status + searchable business keys + gated attachment
 * ============================================================================
 *
 * PROBLEM
 *   Without instrumentation, every message looks the same in the monitor: either
 *   "Completed" or "Failed". Support cannot answer "which order failed?" or
 *   "how many messages were rejected by the business rule?" without reading payloads
 *   one by one.
 *
 * WHAT THIS SCRIPT DOES
 *   1. Sets SAP_MessageProcessingLogCustomStatus — a searchable business status
 *      (maximum 40 alphanumeric characters, stored as the CustomStatus attribute of
 *      the message processing log).
 *   2. Registers business keys as MPL custom header properties, so a message can be
 *      found by order number, customer, document ID, and so on.
 *   3. Writes the payload to the MPL as an attachment ONLY when an externalized
 *      debug switch is on.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   - Place it at the points where the message state changes in a way operations care
 *     about: after validation, after the receiver call, in the Exception Subprocess.
 *   - Populate the input properties/headers before this step (typically from a Content
 *     Modifier that reads them out of the payload).
 *   - Externalize `EnablePayloadLog` (parameter) and pass it into the property
 *     `p_enablePayloadLog`. Default it to false in production.
 *
 * IMPORTANT BEHAVIOUR NOTES
 *   - Script-step properties (`setStringProperty`) become visible in the MPL only when
 *     the log level is Debug or Trace. The CUSTOM STATUS is always relevant, which is
 *     why it is set separately.
 *   - MPL attachments consume the tenant's message processing log storage. Writing a
 *     payload on every message is a quota and performance problem, not a feature.
 *   - `messageLogFactory.getMessageLog(message)` can return null (for example during a
 *     local simulation), so the code always null-checks it.
 */

def Message processData(Message message) {

    // ---------------------------------------------------------------------
    // 1. Read the keys we want to make searchable.
    //    Use a small helper so business keys can come from either a property
    //    (set by a previous step) or a header (set by the sender adapter).
    // ---------------------------------------------------------------------
    def value = { String propertyName, String headerName ->
        def v = message.getProperty(propertyName)
        if (v == null && headerName) {
            v = message.getHeader(headerName, String.class)
        }
        return v == null ? null : v.toString()
    }

    String orderNumber  = value("p_orderNumber", "OrderNumber")
    String customerId   = value("p_customerId", "CustomerId")
    String documentId   = value("p_documentId", null)
    String sourceSystem = value("p_sourceSystem", "SAP_Sender")

    // ---------------------------------------------------------------------
    // 2. Determine the custom status for this step.
    //    Keep a controlled vocabulary per interface instead of free text.
    //    Examples: RECEIVED, VALIDATED, SENT, DLQ, DUPLICATESKIPPED, BUSINESSREJECTED
    // ---------------------------------------------------------------------
    String customStatus = message.getProperty("p_customStatus") ?: "PROCESSED"
    // The documented constraint is at most 40 ALPHANUMERIC characters, so separators
    // are removed and the value is upper-cased to keep the vocabulary searchable and
    // comparable in Monitor. If your tenant accepts other characters, relax this line
    // deliberately — but keep the length guard and the upper-casing.
    customStatus = customStatus.replaceAll("[^A-Za-z0-9]", "").toUpperCase()
    if (customStatus.length() > 40) {
        customStatus = customStatus.substring(0, 40)
    }
    if (!customStatus) {
        customStatus = "UNKNOWN"
    }

    // SAP_MessageProcessingLogCustomStatus is an EXCHANGE PROPERTY, not a header:
    // it must not travel to the receiver.
    message.setProperty("SAP_MessageProcessingLogCustomStatus", customStatus)

    // ---------------------------------------------------------------------
    // 3. Write to the message processing log.
    // ---------------------------------------------------------------------
    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog == null) {
        // No MPL available in this context (e.g. local simulation): nothing to do.
        return message
    }

    // 3a. Searchable custom header properties (persisted, used by Monitor search).
    if (orderNumber)  { messageLog.addCustomHeaderProperty("OrderNumber", orderNumber) }
    if (customerId)   { messageLog.addCustomHeaderProperty("CustomerId", customerId) }
    if (documentId)   { messageLog.addCustomHeaderProperty("DocumentId", documentId) }
    if (sourceSystem) { messageLog.addCustomHeaderProperty("SourceSystem", sourceSystem) }

    // 3b. Short, structured MPL properties.
    //     Use addAttachmentAsString for anything longer than a few words.
    messageLog.setStringProperty("CustomStatus", customStatus)
    messageLog.setStringProperty("Step", message.getProperty("p_stepName") ?: "UNKNOWN")

    // 3c. Payload attachment, only when troubleshooting is explicitly enabled.
    //     The switch must be an externalized parameter, false by default in production.
    boolean payloadLogEnabled = "true".equalsIgnoreCase(
        String.valueOf(message.getProperty("p_enablePayloadLog") ?: "false"))

    if (payloadLogEnabled) {
        String payload = message.getBody(String.class)
        if (payload != null) {
            String contentType = message.getHeader("Content-Type", String.class) ?: "text/plain"
            String attachmentName = "Payload_" + (message.getProperty("p_stepName") ?: "Step")
            messageLog.addAttachmentAsString(attachmentName, payload, contentType)
        }
    }

    return message
}
