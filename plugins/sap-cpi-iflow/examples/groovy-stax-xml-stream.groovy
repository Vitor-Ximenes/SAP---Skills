import com.sap.gateway.ip.core.customdev.util.Message
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.XMLStreamWriter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * ============================================================================
 * StAX streaming transformation for VERY LARGE XML payloads (constant memory)
 * ============================================================================
 *
 * PROBLEM
 *   DOM-based parsers (XmlParser, XmlSlurper) and `message.getBody(java.lang.String)`
 *   load the whole document into the JVM heap. A 200 MB feed becomes several hundred
 *   MB of heap per concurrent message and ends in java.lang.OutOfMemoryError, which
 *   affects every flow running on the same worker node.
 *
 * WHAT THIS SCRIPT DOES
 *   Reads the incoming payload as a stream, walks it event by event with
 *   XMLStreamReader, and writes the transformed document event by event with
 *   XMLStreamWriter into a temporary file on the tenant file system. The message body
 *   is then handed over as a stream backed by that file, and the file is deleted as
 *   soon as the consumer closes the stream.
 *   Heap usage is bounded by the buffer size, not by the payload size.
 *
 * WHEN TO USE IT
 *   Only when the transformation really must be done in a single pass over a large
 *   document. If the payload is a repeating structure, splitting it first (Iterating or
 *   General Splitter with streaming enabled) and transforming each chunk with a normal
 *   mapping is simpler, reviewable and avoids the temporary file altogether.
 *   Check the streaming capability matrix in ../references/performance-and-sizing.md
 *   before choosing this route: one non-streaming step elsewhere in the flow defeats
 *   the purpose of this script.
 *
 * HOW TO WIRE IT IN THE IFLOW
 *   1. Add a Script step immediately after the sender adapter, so the payload is still
 *      a stream and has not been touched by a non-streaming step.
 *   2. Upload this file to the Script step (or place it in a Script Collection and
 *      reference it), keeping `processData` as the entry function.
 *   3. Optionally use the properties set here (`p_itemCount`, `p_streamedOutput`) in a
 *      following Content Modifier or Router.
 *   4. Never place a Message Mapping, XML Schema Validation or CSV converter upstream
 *      of this step on the same payload path.
 *
 * NOTES AND LIMITS
 *   - Namespace declarations and attributes are copied through, so prefixes are
 *     preserved for the elements that pass the filter.
 *   - Comments, processing instructions and CDATA sections are dropped by this
 *     template: add the corresponding cases if your contract requires them.
 *   - The temporary file is deleted when the consumer closes the stream. If a
 *     downstream step never closes it, the file remains until the node restarts:
 *     monitor temporary storage usage (Operations).
 *   - XXE protection is enabled on the XMLInputFactory (no external entities, no DTD).
 *     Keep it that way.
 */

def Message processData(Message message) {

    // Transformation rule: adapt to your contract.
    // Default: drop the <Internal> subtree, copy everything else through unchanged.
    final String DROPPED_ELEMENT = "Internal"

    InputStream inputStream = message.getBody(InputStream.class)
    if (inputStream == null) {
        message.setProperty("p_streamedOutput", "false")
        return message
    }

    // Security: never resolve external entities or DTDs while parsing untrusted XML.
    XMLInputFactory inputFactory = XMLInputFactory.newInstance()
    inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE)
    inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE)

    XMLOutputFactory outputFactory = XMLOutputFactory.newInstance()

    File tempFile = File.createTempFile("cpi-stax-", ".xml")
    XMLStreamReader reader = null
    XMLStreamWriter writer = null
    BufferedOutputStream fileOut = null
    BufferedInputStream rawIn = null

    try {
        rawIn = new BufferedInputStream(inputStream, 64 * 1024)
        fileOut = new BufferedOutputStream(new FileOutputStream(tempFile), 64 * 1024)

        reader = inputFactory.createXMLStreamReader(rawIn)
        writer = outputFactory.createXMLStreamWriter(fileOut, "UTF-8")

        writer.writeStartDocument("UTF-8", "1.0")

        int itemCount = 0
        int skipDepth = 0          // > 0 means we are inside a dropped subtree
        boolean documentStarted = false

        while (reader.hasNext()) {
            int event = reader.next()

            switch (event) {

                case XMLStreamConstants.START_ELEMENT:
                    if (skipDepth > 0) {
                        skipDepth++
                        break
                    }
                    if (DROPPED_ELEMENT.equals(reader.getLocalName())) {
                        skipDepth = 1
                        break
                    }

                    String prefix = reader.getPrefix() ?: ""
                    String nsUri = reader.getNamespaceURI() ?: ""

                    if (nsUri) {
                        writer.writeStartElement(prefix, reader.getLocalName(), nsUri)
                    } else {
                        writer.writeStartElement(reader.getLocalName())
                    }
                    documentStarted = true

                    // Namespaces and attributes must be written AFTER writeStartElement.
                    for (int i = 0; i < reader.getNamespaceCount(); i++) {
                        String nsPrefix = reader.getNamespacePrefix(i)
                        String ns = reader.getNamespaceURI(i)
                        if (!ns) {
                            continue
                        }
                        if (nsPrefix == null || nsPrefix.isEmpty()) {
                            writer.writeDefaultNamespace(ns)
                        } else {
                            writer.writeNamespace(nsPrefix, ns)
                        }
                    }

                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        String aPrefix = reader.getAttributePrefix(i)
                        String aNs = reader.getAttributeNamespace(i)
                        String aName = reader.getAttributeLocalName(i)
                        String aValue = reader.getAttributeValue(i)
                        if (aNs) {
                            writer.writeAttribute(aPrefix ?: "", aNs, aName, aValue)
                        } else {
                            writer.writeAttribute(aName, aValue)
                        }
                    }

                    if ("Item".equalsIgnoreCase(reader.getLocalName())) {
                        itemCount++
                    }
                    break

                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                    if (skipDepth == 0 && documentStarted) {
                        writer.writeCharacters(reader.getText())
                    }
                    break

                case XMLStreamConstants.END_ELEMENT:
                    if (skipDepth > 0) {
                        skipDepth--
                        if (skipDepth == 0) {
                            break
                        }
                        break
                    }
                    writer.writeEndElement()
                    break

                default:
                    // Comments, processing instructions and other events are ignored.
                    break
            }
        }

        writer.writeEndDocument()
        writer.flush()
        writer.close()
        writer = null
        fileOut.close()
        fileOut = null

        message.setProperty("p_itemCount", itemCount)
        message.setProperty("p_streamedOutput", "true")

        // Hand the result over as a stream. The wrapper deletes the temporary file as
        // soon as the next step or adapter closes the stream.
        final File fileToDelete = tempFile
        message.setBody(new FileInputStream(tempFile) {
            @Override
            void close() {
                try {
                    super.close()
                } finally {
                    if (fileToDelete.exists()) {
                        fileToDelete.delete()
                    }
                }
            }
        })

    } catch (Exception e) {
        // Make sure a failed transformation does not leave data behind.
        if (tempFile.exists()) {
            tempFile.delete()
        }
        throw e

    } finally {
        if (writer != null) { try { writer.close() } catch (Exception ignored) { } }
        if (fileOut != null) { try { fileOut.close() } catch (Exception ignored) { } }
        if (reader != null) { try { reader.close() } catch (Exception ignored) { } }
        if (rawIn != null) { try { rawIn.close() } catch (Exception ignored) { } }
    }

    return message
}
