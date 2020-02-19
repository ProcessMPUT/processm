package processm.core.log

import javax.xml.stream.XMLStreamWriter

class XMLXESOutputStream(private val output: XMLStreamWriter) : XESOutputStream {
    override fun write(element: XESElement) {
        TODO("Write element to XML. You may need to remember context: log_id and trace_id.")
    }

    override fun close() {
        output.close()
    }
}