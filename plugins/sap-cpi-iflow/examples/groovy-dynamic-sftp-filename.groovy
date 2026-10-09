import com.sap.gateway.ip.core.customdev.util.Message
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Sets a compliant dynamic target file name on the SFTP/FTP receiver.
 *
 * WHAT IT SOLVES
 * Target systems reject file names they cannot parse or store: spaces, slashes, colons, umlauts,
 * over-long names, names without the agreed extension, and - worst of all - names that differ between
 * two attempts of the same message. This script builds the name from a timestamp, a business key and a
 * whitelisted extension, sanitises it, and sets the documented `CamelFileName` receiver header.
 *
 * WHEN TO USE IT
 * - Before an SFTP/FTP receiver step that must write a business-meaningful file name.
 * - When several files are produced from one message (splitter): the file name must be unique per part.
 * - When the source does not provide a usable name and the adapter's static name would collide.
 * Not needed when a static name or the framework's default is acceptable.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   [Transform steps] -> THIS SCRIPT -> [SFTP receiver]
 * The receiver's "File Name" parameter stays empty (or is overridden by the header); the TARGET
 * DIRECTORY stays an adapter parameter - this script never sets a path.
 *
 * Content Modifier before this script:
 *   p_fileNamePrefix   -> 'ORDER'                         ({{file_name_prefix}})
 *   p_businessKey      -> ${property.p_orderId}           (any business identifier)
 *   p_fileExtension    -> 'csv'                           (whitelist: csv,txt,xml,json,dat,zip)
 *   p_requestTimestamp -> optional, set ONCE per message  (required for retry safety)
 *   p_includeHash      -> 'true' | 'false'                (adds 8 hex chars for uniqueness)
 *   p_maxFileNameLength-> '200'
 *
 * Exchange properties published: p_targetFileName, p_fileNameStable ('true' when the name was derived
 * from an existing p_requestTimestamp instead of the current time).
 *
 * SAP NOTES RESPECTED HERE
 * - `TimeZone.setDefault` is never called (it would change the JVM default time zone for the whole
 *   worker node). UTC is formatted explicitly instead.
 * - The receiver header `CamelFileName` is the documented way to override the configured file name.
 *   When neither the adapter parameter nor the header is set, the framework falls back to the
 *   Exchange ID, which carriers cannot reconcile against a business document.
 * - Retry safety: the timestamp comes from p_requestTimestamp when the flow already set one, so a JMS
 *   redelivery of the same message produces the identical file name instead of a second file.
 */
def Message processData(Message message) {
    String prefix = sanitise(str(message.getProperty("p_fileNamePrefix")) ?: "FILE", 20)
    String key = sanitise(str(message.getProperty("p_businessKey")), 60)
    String extension = allowedExtension(str(message.getProperty("p_fileExtension")))
    boolean includeHash = "true".equalsIgnoreCase(str(message.getProperty("p_includeHash")) ?: "false")
    int maxLength = (int) parseLong(str(message.getProperty("p_maxFileNameLength")), 200L)
    if (maxLength < 20) {
        maxLength = 20
    }

    boolean stable = true
    String stamp = str(message.getProperty("p_requestTimestamp"))
    if (stamp == null) {
        // Formatting UTC explicitly - never touching the JVM default time zone.
        stamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        message.setProperty("p_requestTimestamp", stamp)
        stable = false
    } else {
        // Accept only characters that survive a file name: a caller may have passed a raw ISO string.
        stamp = stamp.replaceAll("[^A-Za-z0-9]", "")
    }

    // Uniqueness: a splitter part uses CamelSplitIndex; a single message gets a short random suffix so
    // two messages that share a business key in the same second cannot overwrite each other.
    String uniqueness = str(message.getHeader("CamelSplitIndex", String.class))
    if (uniqueness == null) {
        uniqueness = UUID.randomUUID().toString().substring(0, 8)
    }
    uniqueness = uniqueness.replaceAll("[^A-Za-z0-9]", "")

    String safeKey = key ?: "NO_KEY"
    StringBuilder name = new StringBuilder()
    name.append(prefix).append('_').append(safeKey).append('_').append(stamp)
    if (includeHash) {
        name.append('_').append(shortHash(safeKey + stamp + uniqueness))
    }
    name.append('_').append(uniqueness)
    name.append('.').append(extension)

    String fileName = truncate(name.toString(), maxLength)
    if (isReserved(fileName)) {
        // Reserved device names of common file servers: prefix instead of failing the flow.
        fileName = "X_" + fileName
    }

    // Documented receiver header. Setting it is what makes the target name dynamic.
    message.setHeader("CamelFileName", fileName)
    message.setProperty("p_targetFileName", fileName)
    message.setProperty("p_fileNameStable", String.valueOf(stable))

    String status
    if (key == null) {
        status = "FILE_NAME_FALLBACK"
    } else if (!stable) {
        status = "FILE_NAME_NEW_TIMESTAMP"
    } else {
        status = "FILE_NAME_STABLE"
    }
    message.setProperty("SAP_MessageProcessingLogCustomStatus", truncate(status, 40))

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("TargetFileName", truncate(fileName, 120))
        messageLog.addCustomHeaderProperty("FileNameStable", String.valueOf(stable))
        if (key == null) {
            messageLog.addAttachmentAsString("FileNameFallback",
                    "prefix=" + prefix + "\nfileName=" + fileName +
                            "\nreason=no business key was provided\n" +
                            "note=The file was written with a NO_KEY name. Supply p_businessKey to make target files reconcilable.", "text/plain")
        }
    }
    return message
}

String sanitise(String value, int max) {
    if (value == null) {
        return null
    }
    String cleaned = value.trim().replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_+", "_")
    // Single-quoted patterns: '$' is literal there and cannot be read as GString interpolation.
    cleaned = cleaned.replaceAll('^[_.]+', '').replaceAll('[_.]+$', '')
    if (cleaned.isEmpty()) {
        return null
    }
    return truncate(cleaned, max)
}

String allowedExtension(String value) {
    // Whitelist, not blacklist: an unexpected extension is replaced instead of being forwarded.
    List<String> allowed = ["csv", "txt", "xml", "json", "dat", "zip"]
    String candidate = value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]", "")
    return allowed.contains(candidate) ? candidate : "dat"
}

boolean isReserved(String fileName) {
    // File servers on Windows-based systems refuse these device names. The check is deliberately
    // conservative and looks at the FIRST token of the name (before the first '_'), because some
    // servers are stricter than the operating system itself. Prefixing a name costs nothing;
    // a rejected transfer costs a support ticket.
    List<String> reserved = ["CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "LPT1", "LPT2", "LPT3"]
    String stem = fileName.contains(".") ? fileName.substring(0, fileName.indexOf('.')) : fileName
    String firstToken = stem.contains("_") ? stem.substring(0, stem.indexOf('_')) : stem
    return reserved.contains(firstToken.toUpperCase())
}

String shortHash(String value) {
    int hash = 0
    for (int i = 0; i < value.length(); i++) {
        hash = (hash * 31 + value.charAt(i)) & 0x7FFFFFFF
    }
    String hex = Integer.toHexString(hash)
    while (hex.length() < 8) {
        hex = "0" + hex
    }
    return hex.substring(0, 8)
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
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
