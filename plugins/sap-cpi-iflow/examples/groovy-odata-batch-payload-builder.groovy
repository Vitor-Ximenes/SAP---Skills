import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Builds a valid OData $batch multipart request body from a list of records.
 *
 * WHAT IT SOLVES
 * Bulk replication to an OData service means one multipart/mixed document carrying many HTTP requests.
 * The framing is unforgiving: CRLF everywhere, one blank line between header block and content, a
 * Content-Length per embedded request that counts BYTES, a boundary that must appear identically in
 * the Content-Type header and in the body, and a closing delimiter. This script assembles all of it
 * from a record list with a single StringBuilder.
 *
 * WHEN TO USE IT
 * - Sending many create/update operations in one HTTP call to an OData V2/V4 service.
 * - Reducing round trips when the receiver supports $batch.
 * Note: the OData V2/OData V4 receiver adapters of Cloud Integration do not support streaming, so the
 * batch body is built in memory by design. Keep an eye on the record count: a batch is a request, not
 * a file transfer. Split very large record sets into several batches.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   [Sender] -> THIS SCRIPT -> [HTTP receiver with Content-Type: multipart/mixed]
 *                              (or the OData receiver, configured for $batch)
 * The script sets the Content-Type header itself (multipart/mixed; boundary=...), so the receiver's
 * configured Content-Type must not override it - configure the adapter accordingly and verify with a
 * trace that the boundary in the header matches the one in the body.
 *
 * INPUT CONTRACT - two supported sources, checked in this order:
 *  1. p_requests : List<Map> built by an upstream Script step. Each entry:
 *        [method:'POST', path:'EntitySet', body:'{"a":1}', contentType:'application/json']
 *        method  GET | POST | PUT | PATCH | DELETE   (GET goes into its own part, writes into a change set)
 *        path    relative to the service root, without a leading slash (the adapter adds the root)
 *        body    optional String, already serialised
 *  2. a JSON array payload, when p_requests is absent. Each array element becomes one POST to
 *     p_entitySet (size assumption: the record array must be small enough for JsonSlurper - split
 *     upstream for large volumes).
 *
 * Content Modifier before this script:
 *   p_entitySet      -> 'BusinessPartners'      (used by the JSON path)
 *   p_useChangeset   -> 'true' | 'false'        (OData V2 write operations must be wrapped in a change
 *                                                set; OData V4 does not require it. Default 'true'.)
 *   p_batchBoundary  -> optional fixed boundary (leave empty to generate one per execution)
 * Exchange properties published: p_batchBoundary, p_batchContentType, p_batchPartCount, p_batchBytes.
 */
def Message processData(Message message) {
    List requests = collectRequests(message)
    if (requests.isEmpty()) {
        throw new IllegalArgumentException("No request descriptors found: provide p_requests or a JSON array payload with p_entitySet")
    }

    boolean useChangeset = !"false".equalsIgnoreCase(str(message.getProperty("p_useChangeset")) ?: "true")
    String boundary = str(message.getProperty("p_batchBoundary"))
    if (boundary == null) {
        boundary = "batch_" + UUID.randomUUID().toString().replace("-", "")
    }
    String changesetBoundary = "changeset_" + boundary
    String crlf = "\r\n"
    StringBuilder out = new StringBuilder(4096)

    List writes = []
    List reads = []
    for (Object entry : requests) {
        Map descriptor = validate(entry)
        if ("GET" == descriptor.get("method")) {
            reads.add(descriptor)
        } else {
            writes.add(descriptor)
        }
    }

    // Part 1: write operations. In OData V2 they MUST be inside a change set; in V4 a change set is
    // optional and only needed when the operations must succeed or fail atomically.
    if (!writes.isEmpty()) {
        if (useChangeset) {
            out.append("--").append(boundary).append(crlf)
            out.append("Content-Type: multipart/mixed; boundary=").append(changesetBoundary).append(crlf)
            out.append("Content-Transfer-Encoding: binary").append(crlf)
            out.append(crlf)
            int contentId = 1
            for (Map descriptor : writes) {
                appendRequest(out, descriptor, changesetBoundary, contentId, crlf)
                contentId++
            }
            out.append("--").append(changesetBoundary).append("--").append(crlf)
        } else {
            int contentId = 1
            for (Map descriptor : writes) {
                appendRequest(out, descriptor, boundary, contentId, crlf)
                contentId++
            }
        }
    }

    // Part 2: read (GET) operations. Each one is its own top-level part; a GET inside a change set is
    // invalid in OData V2, which is why reads are separated here.
    int readId = writes.size() + 1
    for (Map descriptor : reads) {
        appendRequest(out, descriptor, boundary, readId, crlf)
        readId++
    }

    out.append("--").append(boundary).append("--").append(crlf)

    String body = out.toString()
    message.setBody(body)
    // The header and the body must agree on the boundary, so both are derived from the same variable.
    message.setHeader("Content-Type", "multipart/mixed; boundary=" + boundary)

    message.setProperty("p_batchBoundary", boundary)
    message.setProperty("p_batchContentType", "multipart/mixed; boundary=" + boundary)
    message.setProperty("p_batchPartCount", String.valueOf(requests.size()))
    message.setProperty("p_batchWriteCount", String.valueOf(writes.size()))
    message.setProperty("p_batchReadCount", String.valueOf(reads.size()))
    message.setProperty("p_batchBytes", String.valueOf(body.getBytes(StandardCharsets.UTF_8).length))
    message.setProperty("p_batchChangeset", String.valueOf(useChangeset && !writes.isEmpty()))
    message.setProperty("SAP_MessageProcessingLogCustomStatus", "BATCH_BUILT")

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("BatchParts", String.valueOf(requests.size()))
        messageLog.addCustomHeaderProperty("BatchBytes", str(message.getProperty("p_batchBytes")))
        if (requests.size() > 100) {
            messageLog.addAttachmentAsString("BatchSizeWarning",
                    "parts=" + requests.size() + "\nbytes=" + str(message.getProperty("p_batchBytes")) +
                            "\nnote=A very large batch is one long HTTP request: split it into several batches to keep the " +
                            "receiver's request timeout and the tenant's memory footprint under control.", "text/plain")
        }
    }
    return message
}

void appendRequest(StringBuilder out, Map descriptor, String boundary, int contentId, String crlf) {
    String method = (String) descriptor.get("method")
    String path = (String) descriptor.get("path")
    String partBody = (String) descriptor.get("body")
    String partType = (String) descriptor.get("contentType")

    out.append("--").append(boundary).append(crlf)
    out.append("Content-Type: application/http").append(crlf)
    out.append("Content-Transfer-Encoding: binary").append(crlf)
    out.append("Content-ID: ").append(contentId).append(crlf)
    out.append(crlf)                                    // ends the part header block
    out.append(method).append(' ').append(path).append(" HTTP/1.1").append(crlf)
    if (partBody != null) {
        out.append("Content-Type: ").append(partType).append(crlf)
        // Content-Length counts the BYTES of the embedded body: character count breaks on non-ASCII.
        out.append("Content-Length: ").append(partBody.getBytes(StandardCharsets.UTF_8).length).append(crlf)
    }
    out.append(crlf)                                    // ends the embedded request headers
    if (partBody != null) {
        out.append(partBody).append(crlf)
    }
}

List collectRequests(Message message) {
    Object configured = message.getProperty("p_requests")
    if (configured instanceof List && !((List) configured).isEmpty()) {
        return (List) configured
    }

    // Fallback: a JSON array payload becomes one POST per element.
    String entitySet = str(message.getProperty("p_entitySet"))
    if (entitySet == null) {
        throw new IllegalArgumentException("Either p_requests or p_entitySet must be configured")
    }
    Reader reader = message.getBody(java.io.Reader)
    if (reader == null) {
        throw new IllegalArgumentException("p_requests is empty and the payload is empty: nothing to batch")
    }
    Object parsed
    try {
        // Size assumption: the record array is small/medium. For large volumes, split upstream and
        // build the descriptors in a streaming step instead.
        parsed = new JsonSlurper().parse(reader)
    } finally {
        reader.close()
    }
    if (!(parsed instanceof List)) {
        throw new IllegalArgumentException("The payload must be a JSON array of records when p_requests is not used")
    }
    List descriptors = []
    for (Object record : (List) parsed) {
        descriptors.add([method: "POST", path: entitySet, body: JsonOutput.toJson(record), contentType: "application/json"])
    }
    return descriptors
}

Map validate(Object entry) {
    if (!(entry instanceof Map)) {
        throw new IllegalArgumentException("Each request descriptor must be a Map, got: " + (entry == null ? "null" : entry.getClass().getName()))
    }
    Map source = (Map) entry
    String method = str(source.get("method"))
    if (method == null) {
        throw new IllegalArgumentException("Request descriptor without a method")
    }
    method = method.toUpperCase()
    List<String> allowed = ["GET", "POST", "PUT", "PATCH", "DELETE"]
    if (!allowed.contains(method)) {
        throw new IllegalArgumentException("Unsupported HTTP method in batch descriptor: " + method)
    }
    String path = str(source.get("path"))
    if (path == null) {
        throw new IllegalArgumentException("Request descriptor without a path")
    }
    if (path.startsWith("/")) {
        // A leading slash would replace the configured service root instead of extending it.
        path = path.substring(1)
    }
    String body = source.get("body") == null ? null : source.get("body").toString()
    String contentType = str(source.get("contentType")) ?: (body == null ? null : "application/json")

    Map<String, Object> result = [:]
    result.put("method", method)
    result.put("path", path)
    result.put("body", body)
    result.put("contentType", contentType)
    return result
}

String str(Object value) { value == null ? null : value.toString().trim() }
