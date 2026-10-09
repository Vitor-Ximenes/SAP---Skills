import com.sap.gateway.ip.core.customdev.util.Message
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Exception Subprocess helper - captures and classifies the failure that aborted the flow.
 *
 * WHAT IT SOLVES
 * The Exception Subprocess starts with the exception buried in the exchange property
 * `CamelExceptionCaught`. Support needs three different things from it:
 *   1. a SHORT, safe summary that can live in a property, a header or an alert;
 *   2. a CATEGORY that a Router can act on (retry vs. park vs. fail);
 *   3. the FULL detail (stack trace, context) kept out of the message flow, in the MPL.
 * This script produces all three without dumping a stack trace into headers or properties.
 *
 * WHEN TO USE IT
 * As the FIRST step of the Exception Subprocess of every production integration flow, before the
 * step that decides between `End Message`, `Error End` and a DLQ write.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   ... main process ... ---> [Exception Subprocess]
 *   [Error Start] ---> THIS SCRIPT ---> Router on ${property.p_errorRetryable}
 *                                        |-- true  --> Park in DLQ / re-queue, End Message
 *                                        `-- false --> Alert + Error End (or custom fault payload)
 * Before the script (Content Modifier, optional):
 *   p_correlationId  = ${header.X-Correlation-ID}   (or any business correlation key sent by the source)
 *   p_interfaceName  = 'IF_SalesOrder_Out'
 * After the script you can use:
 *   ${property.p_errorCategory}   AUTHENTICATION | TIMEOUT | CONNECTIVITY | TLS | PAYLOAD_FORMAT |
 *                                 VALIDATION | NOT_FOUND | THROTTLED | BACKEND_SERVER_ERROR |
 *                                 REQUEST_REJECTED | IO | UNKNOWN
 *   ${property.p_errorRetryable}  'true' | 'false'  (false = retrying will not help)
 *   ${property.p_errorClass}      fully qualified class name of the ROOT cause
 *   ${property.p_errorSummary}    single-line, redacted, bounded to 500 characters
 *   ${property.p_errorDepth}      number of causes unwrapped
 * The message body is NEVER touched, so an Exception Subprocess that parks the original payload in a
 * Data Store, a JMS queue or an SFTP folder still sees the payload that actually failed.
 *
 * NOTES ON THE API USED
 * `CamelExceptionCaught` is read dynamically and verified with `instanceof Throwable`, so no
 * tenant-specific exception class has to be imported or assumed. Classification uses JDK type names
 * and name fragments only; add your own fragments in `categoryOf` if your tenant reports a specific
 * wrapper type. The documented framework header `CamelHttpResponseCode` is used when a receiver
 * answered with an HTTP status.
 */
def Message processData(Message message) {
    Object caught = message.getProperty("CamelExceptionCaught")

    String errorClass = "java.lang.Exception"
    String errorSummary = "Unclassified error - inspect the MPL attachment Error_Context"
    String category = "UNKNOWN"
    int depth = 0

    if (caught instanceof Throwable) {
        Throwable top = (Throwable) caught
        Throwable root = rootCause(top)
        depth = causeDepth(top)
        errorClass = root.getClass().getName()
        errorSummary = truncate(redact(oneLine(top)), 500)
        category = classify(message, top)
    }

    // Exchange properties: internal, short, safe to use in a Router condition and in an alert body.
    message.setProperty("p_errorCategory", category)
    message.setProperty("p_errorClass", errorClass)
    message.setProperty("p_errorSummary", errorSummary)
    message.setProperty("p_errorDepth", String.valueOf(depth))
    message.setProperty("p_errorRetryable", String.valueOf(isRetryable(category)))

    // Custom MPL status: max 40 alphanumeric characters, otherwise it is silently dropped.
    message.setProperty("SAP_MessageProcessingLogCustomStatus", truncate("ERROR_" + category, 40))

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        // Short, searchable keys. Long or structured content belongs in an attachment.
        messageLog.addCustomHeaderProperty("ErrorCategory", category)
        messageLog.addCustomHeaderProperty("ErrorClass", errorClass)
        messageLog.addCustomHeaderProperty("ErrorRetryable", String.valueOf(isRetryable(category)))
        addKey(messageLog, "CorrelationId", message.getProperty("p_correlationId"))
        addKey(messageLog, "Interface", message.getProperty("p_interfaceName"))
        addKey(messageLog, "BusinessKey", message.getProperty("p_businessKey"))

        if (caught instanceof Throwable) {
            // Full stack trace including every cause: attachment only, never a header or property.
            messageLog.addAttachmentAsString("Error_Stacktrace", stackTrace((Throwable) caught), "text/plain")
        }
        messageLog.addAttachmentAsString("Error_Context", contextSnapshot(message), "text/plain")
    }

    return message
}

Throwable rootCause(Throwable t) {
    Throwable current = t
    int guard = 0
    while (current.getCause() != null && current.getCause() != current && guard < 10) {
        current = current.getCause()
        guard++
    }
    return current
}

int causeDepth(Throwable t) {
    int depth = 0
    Throwable current = t
    while (current != null && current.getCause() != null && current.getCause() != current && depth < 10) {
        current = current.getCause()
        depth++
    }
    return depth
}

String classify(Message message, Throwable top) {
    // 1) A protocol answer is the strongest signal: the receiver told us what was wrong.
    String httpCode = str(message.getHeader("CamelHttpResponseCode", String.class))
    if (httpCode) {
        if (httpCode.startsWith("401") || httpCode.startsWith("403")) return "AUTHENTICATION"
        if (httpCode.startsWith("404")) return "NOT_FOUND"
        if (httpCode.startsWith("408")) return "TIMEOUT"
        if (httpCode.startsWith("429")) return "THROTTLED"
        if (httpCode.startsWith("5")) return "BACKEND_SERVER_ERROR"
        if (httpCode.startsWith("4")) return "REQUEST_REJECTED"
    }
    // 2) Then walk the cause chain from the OUTERMOST exception: the most specific signal wins, so a
    //    SocketTimeoutException wrapping an IOException is reported as TIMEOUT, not as IO.
    Throwable current = top
    int guard = 0
    while (current != null && guard < 10) {
        String category = categoryOf(current)
        if (category != null) {
            return category
        }
        if (current.getCause() == current) {
            break
        }
        current = current.getCause()
        guard++
    }
    return (top instanceof java.io.IOException) ? "IO" : "UNKNOWN"
}

String categoryOf(Throwable t) {
    String name = t.getClass().getName()
    if (name.contains("SocketTimeout") || name.contains("Timeout")) return "TIMEOUT"
    if (name.contains("UnknownHost") || name.contains("Connect") || name.contains("NoRouteToHost")) return "CONNECTIVITY"
    if (name.contains("SSL") || name.contains("Certificate")) return "TLS"
    if (name.contains("SAXParse") || name.contains("Json") || name.contains("XMLStream")) return "PAYLOAD_FORMAT"
    if (name.contains("NumberFormat") || name.contains("IllegalArgument")) return "VALIDATION"
    return null
}

boolean isRetryable(String category) {
    // Transient conditions: a retry (JMS redelivery, a second attempt) can genuinely succeed.
    // Everything else must NOT be retried - retrying a validation error only creates more noise.
    List<String> transientCategories = ["TIMEOUT", "CONNECTIVITY", "THROTTLED", "BACKEND_SERVER_ERROR", "IO"]
    return transientCategories.contains(category)
}

String oneLine(Throwable t) {
    StringBuilder sb = new StringBuilder()
    Throwable current = t
    int depth = 0
    while (current != null && depth < 4) {
        sb.append(current.getClass().getSimpleName())
        String msg = str(current.getMessage())
        if (msg) {
            sb.append(": ").append(msg.replaceAll("\\s+", " ").trim())
        }
        current = current.getCause()
        if (current != null) {
            sb.append(" <- ")
        }
        depth++
    }
    return sb.toString()
}

String redact(String value) {
    if (value == null) {
        return null
    }
    String result = value
    // A summary can end up in an alert mail or an HTTP response: strip anything that looks like a secret.
    result = result.replaceAll("(?i)(authorization\\s*[:=]\\s*)\\S+", '$1<redacted>')
    result = result.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._-]{8,}", '$1<redacted>')
    result = result.replaceAll("(?i)(password\\s*[:=]\\s*)\\S+", '$1<redacted>')
    result = result.replaceAll("(?i)(client_secret\\s*[:=]\\s*)\\S+", '$1<redacted>')
    result = result.replaceAll("(?i)(api[-_]?key\\s*[:=]\\s*)\\S+", '$1<redacted>')
    return result
}

String contextSnapshot(Message message) {
    StringBuilder sb = new StringBuilder()
    sb.append("interface=").append(str(message.getProperty("p_interfaceName")) ?: "<unset>").append('\n')
    sb.append("correlationId=").append(str(message.getProperty("p_correlationId")) ?: "<unset>").append('\n')
    sb.append("category=").append(str(message.getProperty("p_errorCategory"))).append('\n')
    sb.append("retryable=").append(str(message.getProperty("p_errorRetryable"))).append('\n')
    sb.append("errorClass=").append(str(message.getProperty("p_errorClass"))).append('\n')
    sb.append("summary=").append(str(message.getProperty("p_errorSummary"))).append('\n')
    sb.append("payloadBytes=").append(str(message.getProperty("p_payloadBytes")) ?: "<unset>").append('\n')
    sb.append("note=The payload is unchanged; park it in the DLQ from this subprocess if required.").append('\n')
    return sb.toString()
}

String stackTrace(Throwable t) {
    StringWriter sw = new StringWriter()
    PrintWriter pw = new PrintWriter(sw)
    try {
        t.printStackTrace(pw)
        pw.flush()
        // Local policy cap: a runaway stack trace must not blow up the MPL attachment.
        return truncate(sw.toString(), 20000)
    } finally {
        pw.close()
    }
}

void addKey(def messageLog, String name, Object value) {
    String text = str(value)
    if (text) {
        messageLog.addCustomHeaderProperty(name, truncate(text, 120))
    }
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
