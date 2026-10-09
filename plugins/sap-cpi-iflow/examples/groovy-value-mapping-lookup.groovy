import com.sap.gateway.ip.core.customdev.util.Message

/**
 * Maintainable code-list / value-mapping lookup with an explicit default branch.
 *
 * WHAT IT SOLVES
 * Small code translations (document types, units, status codes, country codes) do not justify a
 * message mapping, but a hardcoded if/else chain is unmaintainable and an unmapped code silently
 * produces an empty target field that only fails in the receiver system. This script keeps the code
 * list as reviewable rows of data, resolves forward or reverse, applies a MANDATORY default, and makes
 * every unmapped code searchable in the Message Processing Log so the list can be improved from
 * production evidence.
 *
 * WHEN TO USE IT
 * - Translating codes between two systems where the list fits in a script (tens of rows).
 * - Bulk translation: p_sourceValues (a List) is mapped in one call, e.g. for all items of an order.
 * For thousands of rows, or when business users must maintain the list, use an external source
 * (a mapping step, a Partner Directory parameter or a Data Store entry) and keep the same contract.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   [Source] -> THIS SCRIPT -> mapping / receiver
 *   Router on ${property.p_mappingHit} when the flow must behave differently for unmapped codes.
 * Content Modifier before this script:
 *   p_sourceValue    -> ${header.DocType}          single value to translate
 *   p_sourceValues   -> a List of values           (alternative: batch mode)
 *   p_defaultTarget  -> 'UNKNOWN'                  MANDATORY: the outcome for an unmapped code
 *   p_direction      -> 'FORWARD' (default) | 'REVERSE'
 *   p_mappingName    -> 'DocType_CRM_to_S4'        used for MPL keys and attachments
 *   p_failOnUnmapped -> 'true' | 'false'           true = throw instead of using the default
 *   p_codeListOverride -> optional rows 'SOURCE|TARGET' separated by newline or ';'
 *
 * Exchange properties published (single mode):
 *   p_targetValue, p_mappingHit, p_mappingName, p_unmappedValue
 * (batch mode): p_targetValues (List, same order as the input), p_unmappedValues (List),
 *   p_mappingHitCount, p_mappingMissCount, p_mappingHitRate
 *
 * WHY THE DEFAULT BRANCH IS MANDATORY
 * An unmapped code has three possible outcomes: a defined replacement value, a hard failure, or a
 * silent null. Only the first two are debuggable. Because the script refuses to run without
 * p_defaultTarget, "silent null" is not an option - the flow always states what should happen.
 */
def Message processData(Message message) {
    String defaultTarget = str(message.getProperty("p_defaultTarget"))
    if (defaultTarget == null) {
        throw new IllegalArgumentException("p_defaultTarget is mandatory: an unmapped code must have a defined outcome")
    }
    String direction = (str(message.getProperty("p_direction")) ?: "FORWARD").toUpperCase()
    if (direction != "FORWARD" && direction != "REVERSE") {
        throw new IllegalArgumentException("p_direction must be FORWARD or REVERSE, got: " + direction)
    }
    String mappingName = str(message.getProperty("p_mappingName")) ?: "unnamed"
    boolean failOnUnmapped = "true".equalsIgnoreCase(str(message.getProperty("p_failOnUnmapped")) ?: "false")

    // The code list is DATA: one reviewable row per mapping, comments allowed with '#'. Keeping it in
    // the script (instead of an if/else chain) makes a change a one-line diff that survives transport.
    List<String> rows = parseRows(str(message.getProperty("p_codeListOverride")))
    if (rows.isEmpty()) {
        rows = [
                '01|INVOICE',            // CRM document category -> S/4 billing document type
                '02|CREDIT_MEMO',
                '03|DEBIT_MEMO',
                '04|#SKIP#',             // intentionally mapped to a technical marker, not to a business value
        ]
    }
    Map<String, String> mapping = buildMapping(rows)
    Map<String, String> effective = (direction == "REVERSE") ? invert(mapping) : mapping

    // Batch mode: p_sourceValues is a List, typically one entry per payload item.
    Object batch = message.getProperty("p_sourceValues")
    if (batch instanceof List) {
        return processBatch(message, (List) batch, effective, defaultTarget, mappingName, direction, failOnUnmapped)
    }

    String sourceValue = str(message.getProperty("p_sourceValue"))
    boolean mapped = sourceValue != null && effective.containsKey(sourceValue)
    String target = mapped ? effective.get(sourceValue) : defaultTarget

    message.setProperty("p_targetValue", target)
    message.setProperty("p_mappingHit", String.valueOf(mapped))
    message.setProperty("p_mappingName", mappingName)
    message.setProperty("p_mappingSize", String.valueOf(effective.size()))

    if (mapped) {
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "MAPPED")
        // A mapped code whose target is an empty string is still a mapping: containsKey() decides the
        // hit, never the truthiness of the value.
        if (target != null && target.isEmpty()) {
            message.setProperty("SAP_MessageProcessingLogCustomStatus", "MAPPED_EMPTY_TARGET")
        }
    } else {
        reportUnmapped(message, sourceValue, defaultTarget, mappingName, direction, failOnUnmapped, null)
    }
    return message
}

Message processBatch(Message message, List sourceValues, Map<String, String> effective, String defaultTarget,
                     String mappingName, String direction, boolean failOnUnmapped) {
    List<String> targets = []
    List<String> unmapped = []
    int hits = 0
    for (Object raw : sourceValues) {
        String sourceValue = str(raw)
        if (sourceValue != null && effective.containsKey(sourceValue)) {
            targets.add(effective.get(sourceValue))
            hits++
        } else {
            targets.add(defaultTarget)
            unmapped.add(sourceValue == null ? "<empty>" : sourceValue)
        }
    }
    int misses = sourceValues.size() - hits
    message.setProperty("p_targetValues", targets)
    message.setProperty("p_unmappedValues", unmapped)
    message.setProperty("p_mappingHitCount", String.valueOf(hits))
    message.setProperty("p_mappingMissCount", String.valueOf(misses))
    message.setProperty("p_mappingHitRate", sourceValues.isEmpty() ? "0" : String.valueOf(Math.round(100.0 * hits / sourceValues.size())))
    message.setProperty("p_mappingName", mappingName)

    if (misses == 0) {
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "MAPPED")
        return message
    }
    reportUnmapped(message, unmapped.join(", "), defaultTarget, mappingName, direction, failOnUnmapped, misses)
    message.setProperty("p_mappingHit", "false")
    return message
}

void reportUnmapped(Message message, String sourceValue, String defaultTarget, String mappingName,
                    String direction, boolean failOnUnmapped, Integer missCount) {
    message.setProperty("p_mappingHit", "false")
    message.setProperty("p_unmappedValue", sourceValue ?: "")
    if (failOnUnmapped) {
        throw new IllegalArgumentException(
                "No mapping for value '" + (sourceValue ?: "<empty>") + "' in " + direction + " direction (mapping " + mappingName + ")")
    }
    message.setProperty("SAP_MessageProcessingLogCustomStatus", "UNMAPPED_CODE")

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        // Searchable: this is the feed for the next code-list review.
        messageLog.addCustomHeaderProperty("UnmappedCode", truncate(sourceValue ?: "<empty>", 60))
        messageLog.addCustomHeaderProperty("MappingName", truncate(mappingName, 60))
        messageLog.addCustomHeaderProperty("MappingDirection", direction)
        if (missCount != null) {
            messageLog.addCustomHeaderProperty("UnmappedCount", String.valueOf(missCount))
        }
        messageLog.addAttachmentAsString("UnmappedCodeDetail",
                "mapping=" + mappingName + "\ndirection=" + direction +
                        "\nsourceValue=" + (sourceValue ?: "<empty>") +
                        "\ndefaultApplied=" + defaultTarget +
                        "\nnote=Add the missing row to the code list (SOURCE|TARGET) or confirm that the default is correct.", "text/plain")
    }
}

List<String> parseRows(String override) {
    if (override == null || override.trim().isEmpty()) {
        return []
    }
    return override.split("[\\n;]").toList()
}

Map<String, String> buildMapping(List<String> rows) {
    Map<String, String> mapping = new LinkedHashMap<String, String>()
    for (String row : rows) {
        String line = row == null ? "" : row.trim()
        if (line.isEmpty() || line.startsWith("#")) {
            continue   // blank lines and comments keep the list readable
        }
        int separator = line.indexOf('|')
        if (separator < 1 || separator == line.length() - 1) {
            throw new IllegalArgumentException("Malformed code list row (expected SOURCE|TARGET): " + line)
        }
        mapping.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim())
    }
    if (mapping.isEmpty()) {
        throw new IllegalArgumentException("The code list is empty after parsing: refusing to run without a mapping")
    }
    return mapping
}

Map<String, String> invert(Map<String, String> mapping) {
    Map<String, String> inverted = new LinkedHashMap<String, String>()
    mapping.each { key, value ->
        // On reverse collisions the first row wins: the list order is the priority order.
        if (!inverted.containsKey(value)) {
            inverted.put(value, key)
        }
    }
    return inverted
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
