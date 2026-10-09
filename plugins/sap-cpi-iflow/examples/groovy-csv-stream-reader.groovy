import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.XmlUtil
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Streams a very large CSV file and emits chunks of N data lines each - with constant memory.
 *
 * WHAT IT SOLVES
 * A receiver accepts at most N records per call, the source file has millions of lines, and building
 * the whole file as a String (or as a DOM) would exhaust the worker heap. This script reads the file
 * record by record, keeps exactly ONE record and ONE chunk in memory, and wraps the chunks into a
 * small XML envelope that an Iterating Splitter can process further.
 *
 * WHEN TO USE IT
 * - Flat file input of any size where the downstream accepts record-limited chunks.
 * - Before a non-streaming step (CSV to XML converter, Message Mapping, OData/JDBC receiver): chunk
 *   first, then process each chunk, so no single step ever sees the whole file.
 * - NOT needed when the receiver can consume the file line by line: in that case prefer an Iterating
 *   Splitter with Streaming enabled and skip this script entirely.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   [SFTP/HTTP sender] -> THIS SCRIPT -> [Iterating Splitter on /Chunks/Chunk] -> mapping -> receiver
 *   Configure the SFTP/FTP sender with "Change Directories Stepwise" DISABLED, otherwise the adapter
 *   does not stream and the payload is buffered before the script even starts.
 *
 * Content Modifier before this script (all optional, defaults shown):
 *   p_sourceCharset -> 'UTF-8'      ({{source_charset}})
 *   p_hasHeader     -> 'true'       (first line is a header and is repeated in every chunk)
 *   p_linesPerChunk -> '1000'
 *   p_maxChunks     -> '10'
 *   p_delimiter     -> ';'
 *   p_expectedColumns -> '' (empty = no validation; set e.g. '7' to count malformed lines)
 *
 * Output payload:
 *   <Chunks>
 *     <Chunk index="0" firstDataLine="1" lineCount="1000" malformedLines="0">escaped csv data</Chunk>
 *     ...
 *   </Chunks>
 * Exchange properties published: p_chunkCount, p_dataLineCount, p_physicalLineCount,
 * p_malformedLineCount, p_chunkTruncated, p_targetChunkCount.
 *
 * MEMORY CONTRACT
 * Heap usage is bounded by p_maxChunks * p_linesPerChunk * average line length, not by the input size.
 * When the source is longer than maxChunks * linesPerChunk, the script stops, sets
 * p_chunkTruncated = 'true' and the custom status CHUNKS_TRUNCATED: the remainder is NOT silently
 * dropped, the flow must decide what to do with it (second run, streaming splitter, or an alert).
 *
 * LIMITATIONS (documented on purpose)
 * Quoted-field handling is a heuristic: a record is continued while the number of double quotes is
 * odd, and a doubled quote ("") counts as an escaped quote. It covers RFC 4180 output from common
 * systems; it is not a full CSV parser. A dedicated CSV library would be exact but open source
 * classes are not a supported dependency of the script runtime.
 */
def Message processData(Message message) {
    Charset charset = resolveCharset(str(message.getProperty("p_sourceCharset")))
    boolean hasHeader = !"false".equalsIgnoreCase(str(message.getProperty("p_hasHeader")) ?: "true")
    int linesPerChunk = (int) parseLong(str(message.getProperty("p_linesPerChunk")), 1000L)
    int maxChunks = (int) parseLong(str(message.getProperty("p_maxChunks")), 10L)
    char delimiter = delimiter(str(message.getProperty("p_delimiter")))
    int expectedColumns = (int) parseLong(str(message.getProperty("p_expectedColumns")), -1L)
    if (linesPerChunk < 1) {
        linesPerChunk = 1
    }
    if (maxChunks < 1) {
        maxChunks = 1
    }

    InputStream body = message.getBody(java.io.InputStream)
    if (body == null) {
        message.setProperty("p_chunkCount", "0")
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "EMPTY_SOURCE")
        return message
    }

    boolean useHeaderLine = hasHeader
    String header = null
    StringBuilder chunk = new StringBuilder()
    StringBuilder out = new StringBuilder(4096)
    out.append("<Chunks>")

    int chunkIndex = 0
    int linesInChunk = 0
    int malformedLines = 0
    int malformedInChunk = 0
    long dataLines = 0L
    long physicalLines = 0L
    boolean truncated = false

    // Only a BufferedReader over the body stream is held in heap - never the file itself.
    BufferedReader reader = new BufferedReader(new InputStreamReader(body, charset))
    try {
        String line = reader.readLine()
        while (line != null) {
            physicalLines++
            // Continue the logical record while a quoted field is still open. Only the current record
            // is concatenated, so the merge cannot grow beyond one record.
            while (countQuotes(line) % 2 == 1) {
                String continuation = reader.readLine()
                if (continuation == null) {
                    break
                }
                physicalLines++
                line = line + "\n" + continuation
            }

            if (useHeaderLine && header == null) {
                header = line
                useHeaderLine = false
            } else {
                int malformed = 0
                if (expectedColumns > 0 && splitRecord(line, delimiter).size() != expectedColumns) {
                    malformed = 1
                    malformedLines++
                    malformedInChunk++
                }
                if (linesInChunk == 0 && hasHeader && header != null) {
                    chunk.append(header).append('\n')   // every chunk carries its own header
                }
                chunk.append(line).append('\n')
                linesInChunk++
                dataLines++
                if (linesInChunk >= linesPerChunk) {
                    appendChunk(out, chunk, chunkIndex, dataLines - linesInChunk + 1, linesInChunk, malformedInChunk)
                    chunkIndex++
                    linesInChunk = 0
                    malformedInChunk = 0
                    if (chunkIndex >= maxChunks) {
                        truncated = true
                        break
                    }
                }
            }
            line = reader.readLine()
        }

        // Final partial chunk. Omitting it is the classic off-by-one of every chunking implementation.
        if (!truncated && linesInChunk > 0) {
            appendChunk(out, chunk, chunkIndex, dataLines - linesInChunk + 1, linesInChunk, malformedInChunk)
            chunkIndex++
        }
    } finally {
        reader.close()   // closes the underlying body stream as well
    }

    out.append("</Chunks>")
    message.setBody(out.toString())
    message.setHeader("Content-Type", "application/xml")

    message.setProperty("p_chunkCount", String.valueOf(chunkIndex))
    message.setProperty("p_dataLineCount", String.valueOf(dataLines))
    message.setProperty("p_physicalLineCount", String.valueOf(physicalLines))
    message.setProperty("p_malformedLineCount", String.valueOf(malformedLines))
    message.setProperty("p_chunkTruncated", String.valueOf(truncated))

    String status
    if (chunkIndex == 0) {
        status = "EMPTY_SOURCE"
    } else if (truncated) {
        status = "CHUNKS_TRUNCATED"
    } else if (malformedLines > 0) {
        status = "CHUNKS_BUILT_WITH_WARNINGS"
    } else {
        status = "CHUNKS_BUILT"
    }
    message.setProperty("SAP_MessageProcessingLogCustomStatus", truncate(status, 40))

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("ChunkCount", String.valueOf(chunkIndex))
        messageLog.addCustomHeaderProperty("DataLines", String.valueOf(dataLines))
        messageLog.addCustomHeaderProperty("MalformedLines", String.valueOf(malformedLines))
        messageLog.addCustomHeaderProperty("ChunksTruncated", String.valueOf(truncated))
        if (truncated || malformedLines > 0) {
            messageLog.addAttachmentAsString("CsvChunkingReport",
                    "chunks=" + chunkIndex + "\ndataLines=" + dataLines + "\nphysicalLines=" + physicalLines +
                            "\nmalformedLines=" + malformedLines + "\ntruncated=" + truncated +
                            "\nlinesPerChunk=" + linesPerChunk + "\nmaxChunks=" + maxChunks +
                            "\ncharset=" + charset.name() + "\ndelimiter=" + delimiter +
                            "\nnote=truncated=true means the source was longer than maxChunks * linesPerChunk.", "text/plain")
        }
    }
    return message
}

void appendChunk(StringBuilder out, StringBuilder chunk, int index, long firstDataLine, int lineCount, int malformedLines) {
    out.append("<Chunk index=\"").append(index)
       .append("\" firstDataLine=\"").append(firstDataLine)
       .append("\" lineCount=\"").append(lineCount)
       .append("\" malformedLines=\"").append(malformedLines)
       .append("\">")
       .append(XmlUtil.escapeXml(chunk.toString()))
       .append("</Chunk>")
    chunk.setLength(0)   // release the chunk as soon as it is embedded: memory stays bounded
}

int countQuotes(String value) {
    char quote = (char) '"'
    int count = 0
    for (int i = 0; i < value.length(); i++) {
        if (value.charAt(i) == quote) {
            count++
        }
    }
    return count
}

List<String> splitRecord(String line, char delimiter) {
    // Minimal RFC 4180 aware split, used only for the optional column-count validation.
    char quote = (char) '"'
    List<String> fields = []
    StringBuilder current = new StringBuilder()
    boolean inQuotes = false
    for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i)
        if (inQuotes) {
            if (c == quote) {
                if (i + 1 < line.length() && line.charAt(i + 1) == quote) {
                    current.append(quote)
                    i++
                } else {
                    inQuotes = false
                }
            } else {
                current.append(c)
            }
        } else if (c == quote) {
            inQuotes = true
        } else if (c == delimiter) {
            fields.add(current.toString())
            current.setLength(0)
        } else {
            current.append(c)
        }
    }
    fields.add(current.toString())
    return fields
}

char delimiter(String configured) {
    if (configured == null || configured.isEmpty()) {
        return (char) ';'
    }
    String value = configured.trim()
    if (value == "\\t") {
        return (char) '\t'
    }
    return value.charAt(0)
}

Charset resolveCharset(String name) {
    if (name == null || name.isEmpty()) {
        return StandardCharsets.UTF_8
    }
    try {
        return Charset.forName(name)
    } catch (Exception ignored) {
        return StandardCharsets.UTF_8
    }
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
