import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.Reader
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * ============================================================================
 * JSON enrichment for SMALL TO MEDIUM payloads (Reader-based, no String copy)
 * ============================================================================
 *
 * PROBLEM
 *   `message.getBody(java.lang.String)` followed by `new JsonSlurper().parseText(text)`
 *   creates at least two full copies of the payload in the heap (the String and the
 *   parsed object graph) before any work is done.
 *
 * WHAT THIS SCRIPT DOES
 *   Parses the payload straight from a Reader, extracts business keys into exchange
 *   properties, adds two metadata fields, and writes the result back as COMPACT JSON.
 *   For anything above a few megabytes, use the Jackson streaming approach in
 *   groovy-jackson-json-stream.groovy instead: JsonSlurper builds the complete object
 *   graph in memory and is not a streaming parser.
 *
 * SIZE ASSUMPTION — IMPORTANT
 *   This script is safe for payloads in the order of a few megabytes at most, and only
 *   when the flow does not process many such messages concurrently. If your payloads
 *   can be larger, or if the volume is high, replace it with a streaming parser, or
 *   split the payload first (Iterating Splitter with streaming enabled).
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   1. Place the Script step after the JSON payload has been received or produced by a
 *      converter, and before the receiver adapter.
 *   2. Read the properties it sets (`p_documentId`, `p_recordCount`, `p_processedAt`)
 *      in a following Content Modifier, Router or MPL logging step.
 *   3. Set `Content-Type` on the receiver channel, not here, unless the payload type
 *      actually changes.
 */

def Message processData(Message message) {

    Reader reader = message.getBody(Reader.class)
    if (reader == null) {
        // No payload: leave the message untouched rather than failing the flow.
        message.setProperty("p_documentId", null)
        return message
    }

    def data
    try {
        data = new JsonSlurper().parse(reader)
    } catch (Exception e) {
        // A malformed payload is a terminal, non-retryable error: let the Exception
        // Subprocess classify it (see groovy-exception-details-capture.groovy).
        throw new IllegalArgumentException("Incoming payload is not valid JSON: ${e.message}", e)
    } finally {
        try { reader.close() } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------------
    // 1. Extract business keys into EXCHANGE PROPERTIES.
    //    Properties stay inside the flow; headers would be sent to the receiver.
    // ---------------------------------------------------------------------
    String processedAt = OffsetDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
    message.setProperty("p_processedAt", processedAt)

    if (data instanceof Map) {
        if (data.id != null) {
            message.setProperty("p_documentId", data.id.toString())
        }
        if (data.orderNumber != null) {
            message.setProperty("p_orderNumber", data.orderNumber.toString())
        }
        if (data.items instanceof List) {
            message.setProperty("p_recordCount", data.items.size())
        }

        // -----------------------------------------------------------------
        // 2. Enrich with processing metadata. Keep it to fields the receiver
        //    actually needs: every extra field is a contract you must maintain.
        // -----------------------------------------------------------------
        data.processedBy = "SAP-Integration-Suite"
        data.processedAt = processedAt

    } else if (data instanceof List) {
        message.setProperty("p_recordCount", data.size())
    }

    // ---------------------------------------------------------------------
    // 3. Write back COMPACT JSON.
    //    JsonOutput.toJson produces a compact document. Never use prettyPrint for
    //    payloads that are transmitted: indentation can double the size for nothing.
    // ---------------------------------------------------------------------
    message.setBody(JsonOutput.toJson(data))

    return message
}
