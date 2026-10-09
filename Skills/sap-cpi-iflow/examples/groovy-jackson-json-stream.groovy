import com.sap.gateway.ip.core.customdev.util.Message
import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Filters/transforms a large JSON array of records with the Jackson streaming API, record by record.
 *
 * WHAT IT SOLVES
 * A JSON document with hundreds of thousands of records cannot be parsed with JsonSlurper without
 * loading the whole tree into heap. Jackson's JsonParser/JsonGenerator work token by token: the input
 * is never materialised, and only ONE record plus the produced output are held in memory.
 *
 * WHEN TO USE IT
 * - Large JSON payloads (tens of MB and above) with a flat array of records.
 * - Filtering by a field value, renaming a field, adding a constant field, dropping a field.
 * - Before a non-streaming step: chunk or reduce the document first, then map.
 * For small/medium JSON prefer JsonSlurper over a Reader; for pure "extract one value" tasks a
 * Content Modifier with an XPath/JSON expression is cheaper than a script.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   [Sender] -> THIS SCRIPT -> receiver (HTTP/OData/SFTP) or Splitter
 * Content Modifier before this script (all optional unless stated):
 *   p_filterField   -> field name to test, e.g. 'status'      (empty = keep every record)
 *   p_filterValue   -> value to keep, e.g. 'ACTIVE'           (comparison is case-insensitive)
 *   p_filterAction  -> 'KEEP_MATCH' (default) or 'DROP_MATCH'
 *   p_renameFrom    -> field to rename, e.g. 'customerId'
 *   p_renameTo      -> new field name, e.g. 'Customer'
 *   p_addField      -> constant field to add, e.g. 'sourceSystem'   (via p_addFieldValue)
 *   p_addFieldValue -> value of the constant field, e.g. 'CRM'
 *   p_dropField     -> field to remove from every record
 * Exchange properties published: p_recordsRead, p_recordsWritten, p_recordsSkipped, p_outputBytes.
 *
 * IMPORTANT - RUNTIME SUPPORT
 * Jackson is a third-party library. SAP scripting guidelines state that only the officially supported
 * script APIs and the native Groovy APIs are guaranteed (direct usage of open source classes is not
 * supported). Verify the import is supported in your tenant's script runtime before relying on it; if
 * it is not, use JsonSlurper with a Reader for medium payloads plus a Splitter, or transform the JSON
 * upstream. This script keeps all Jackson usage in two places (creating the parser and the per-record
 * generator) so swapping the library is a local change.
 *
 * MEMORY CONTRACT
 * Input: constant (one token at a time). Output: the filtered document is produced in memory because it
 * IS the new payload - if the output is also too large for the worker, split the source upstream or
 * forward each record to a receiver inside the loop instead of accumulating it.
 */
def Message processData(Message message) {
    InputStream body = message.getBody(java.io.InputStream)
    if (body == null) {
        message.setProperty("p_recordsRead", "0")
        message.setProperty("p_recordsWritten", "0")
        return message
    }

    String filterField = str(message.getProperty("p_filterField"))
    String filterValue = str(message.getProperty("p_filterValue"))
    boolean dropMatch = "DROP_MATCH".equalsIgnoreCase(str(message.getProperty("p_filterAction")) ?: "KEEP_MATCH")
    String renameFrom = str(message.getProperty("p_renameFrom"))
    String renameTo = str(message.getProperty("p_renameTo"))
    String addField = str(message.getProperty("p_addField"))
    String addFieldValue = str(message.getProperty("p_addFieldValue")) ?: ""
    String dropField = str(message.getProperty("p_dropField"))

    if (renameFrom != null && renameTo == null) {
        throw new IllegalArgumentException("p_renameTo must be set when p_renameFrom is set")
    }

    JsonFactory factory = new JsonFactory()
    JsonParser parser = null
    ByteArrayOutputStream output = new ByteArrayOutputStream(8192)
    JsonGenerator writer = null
    int recordsRead = 0
    int recordsWritten = 0

    try {
        parser = factory.createParser(body)
        // Position on the first token: the contract is "the payload is an array of records".
        JsonToken first = parser.nextToken()
        if (first == null) {
            message.setBody("[]")
            message.setProperty("p_recordsRead", "0")
            message.setProperty("p_recordsWritten", "0")
            return message
        }
        if (first != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException(
                    "Expected a JSON array of records but found " + first + ". Split the document or adapt the script.")
        }

        writer = factory.createGenerator(output, JsonEncoding.UTF8)
        writer.writeStartArray()

        // Each record is rendered into its own small buffer first. A record can therefore be dropped
        // after it has been read, without ever writing a partial object into the output.
        ByteArrayOutputStream recordBuffer = new ByteArrayOutputStream(1024)
        while (true) {
            JsonToken token = parser.nextToken()
            // Guard against truncated input: nextToken() returns null at end of input.
            if (token == null || token == JsonToken.END_ARRAY) {
                if (token == null) {
                    throw new IllegalArgumentException("The JSON array of records is not terminated (truncated payload)")
                }
                break
            }
            recordsRead++
            recordBuffer.reset()
            boolean keep = transformRecord(factory, parser, recordBuffer, filterField, filterValue, dropMatch,
                    renameFrom, renameTo, addField, addFieldValue, dropField)
            if (keep) {
                // Flush the generator before writing raw bytes into the same stream, and emit the
                // element separator here: the generator cannot know about bytes it did not write.
                writer.flush()
                if (recordsWritten > 0) {
                    output.write((int) (',' as char))
                }
                output.write(recordBuffer.toByteArray())
                recordsWritten++
            }
        }
        writer.writeEndArray()
        writer.flush()
    } catch (IllegalArgumentException e) {
        throw e   // contract violations already carry a precise message
    } catch (Exception e) {
        // Jackson reports malformed or truncated input as a JsonProcessingException: translate it into
        // a plain JDK exception so the Exception Subprocess sees the same kind of failure as elsewhere.
        throw new IllegalArgumentException("Malformed JSON payload: " + e.getMessage(), e)
    } finally {
        if (parser != null) {
            try { parser.close() } catch (Exception ignored) { }
        }
        if (writer != null) {
            try { writer.close() } catch (Exception ignored) { }
        }
        try { body.close() } catch (Exception ignored) { }
    }

    byte[] result = output.toByteArray()
    message.setBody(result)
    message.setHeader("Content-Type", "application/json")
    message.setProperty("p_recordsRead", String.valueOf(recordsRead))
    message.setProperty("p_recordsWritten", String.valueOf(recordsWritten))
    message.setProperty("p_recordsSkipped", String.valueOf(recordsRead - recordsWritten))
    message.setProperty("p_outputBytes", String.valueOf(result.length))

    String status = (recordsWritten == 0) ? "JSON_FILTER_EMPTY" : "JSON_FILTERED"
    message.setProperty("SAP_MessageProcessingLogCustomStatus", truncate(status, 40))

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("RecordsRead", String.valueOf(recordsRead))
        messageLog.addCustomHeaderProperty("RecordsWritten", String.valueOf(recordsWritten))
        if (recordsWritten == 0 && recordsRead > 0) {
            messageLog.addAttachmentAsString("JsonFilterReport",
                    "filterField=" + filterField + "\nfilterValue=" + filterValue +
                            "\nfilterAction=" + (dropMatch ? "DROP_MATCH" : "KEEP_MATCH") +
                            "\nrecordsRead=" + recordsRead + "\nrecordsWritten=0" +
                            "\nnote=Every record was filtered out; check the filter configuration before assuming an empty source.", "text/plain")
        }
    }
    return message
}

boolean transformRecord(JsonFactory factory, JsonParser parser, ByteArrayOutputStream recordBuffer,
                        String filterField, String filterValue, boolean dropMatch,
                        String renameFrom, String renameTo, String addField, String addFieldValue,
                        String dropField) {
    JsonGenerator record = factory.createGenerator(recordBuffer, JsonEncoding.UTF8)
    boolean keep = true
    boolean matched = false
    try {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            // Arrays of scalars are legal JSON: keep them as-is, they cannot be filtered by field.
            record.copyCurrentStructure(parser)
        } else {
            record.writeStartObject()
            while (true) {
                JsonToken fieldToken = parser.nextToken()
                if (fieldToken == null) {
                    throw new IllegalArgumentException("The JSON record is not terminated (truncated payload)")
                }
                if (fieldToken == JsonToken.END_OBJECT) {
                    break
                }
                String fieldName = parser.getCurrentName()
                parser.nextToken()   // move from FIELD_NAME to the value (or to a nested structure start)
                if (filterField != null && filterField == fieldName && parser.currentToken().isScalarValue()) {
                    matched = (str(parser.getText())?.equalsIgnoreCase(filterValue) ?: false)
                }
                if (dropField != null && dropField == fieldName) {
                    parser.skipChildren()
                } else if (renameFrom != null && renameFrom == fieldName) {
                    record.writeFieldName(renameTo)
                    record.copyCurrentStructure(parser)
                } else {
                    record.writeFieldName(fieldName)
                    record.copyCurrentStructure(parser)
                }
            }
            if (addField != null) {
                record.writeStringField(addField, addFieldValue)
            }
            record.writeEndObject()
        }
        record.flush()
        if (filterField != null) {
            // A record without the filter field counts as matched=false; DROP_MATCH then keeps it.
            keep = dropMatch ? !matched : matched
        }
        return keep
    } finally {
        record.close()
    }
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
