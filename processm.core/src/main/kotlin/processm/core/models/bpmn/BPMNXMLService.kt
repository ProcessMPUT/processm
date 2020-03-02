package processm.core.models.bpmn

import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import processm.core.models.bpmn.jaxb.TDefinitions
import java.io.InputStream
import java.io.OutputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
import javax.xml.bind.UnmarshallerHandler
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.events.*


sealed class BPMNXMLService {

    private class Processor(val handler: UnmarshallerHandler, val reader: XMLEventReader) {
        fun run(): Any? {
            while (reader.hasNext())
                process(reader.nextEvent())
            return handler.result
        }

        private fun process(event: XMLEvent) {
            if (event.isStartDocument)
                handler.startDocument()
            else if (event.isStartElement)
                startElement(event.asStartElement())
            else if (event.isEndElement)
                endElement(event.asEndElement())
            else if (event.isEndDocument)
                handler.endDocument()
            else if (event.isCharacters)
                characters(event.asCharacters())
            else
                println("${event.eventType} || " + event.javaClass)
        }

        private val buffer = StringBuffer()

        fun characters(element: Characters) {
            buffer.append(element.data)
            if (!reader.hasNext() || !reader.peek().isCharacters) {
                handler.characters(buffer.toString().toCharArray(), 0, buffer.length)
                buffer.delete(0, buffer.length)
            }
        }

        fun qname(name: QName): String {
            return if (!name.prefix.isEmpty())
                name.prefix + ":" + name.localPart
            else
                name.localPart
        }

        fun endElement(element: EndElement) {
            handler.endElement(
                element.name.namespaceURI,
                element.name.localPart,
                qname(element.name)
            )
            (element.namespaces as Iterator<Namespace>).forEach { handler.endPrefixMapping(it.prefix) }
        }

        fun startElement(element: StartElement) {
            (element.namespaces as Iterator<Namespace>).forEach {
                if (it.namespaceURI != null)
                    handler.startPrefixMapping(
                        it.prefix,
                        it.namespaceURI
                    )
            }
            try {
                handler.startElement(
                    element.name.namespaceURI,
                    element.name.localPart,
                    qname(element.name),
                    attributes(element)
                )
            } catch (e: NumberFormatException) {
                println(e)
            } catch (e: IllegalArgumentException) {
                println(e)
            }
        }

        fun attributes(event: StartElement): Attributes? {
            val result = AttributesImpl()
            val i: Iterator<*> = event.attributes
            while (i.hasNext()) {
                val staxAttr = i.next() as Attribute
                val name = staxAttr.name
                var qName = qname(name)
                val type = staxAttr.dtdType
                val value = staxAttr.value
                result.addAttribute(name.namespaceURI, name.localPart, qName, type, value)
            }
            return result
        }
    }

    companion object {
        internal fun load(inp: InputStream): TDefinitions {
            val u = JAXBContext.newInstance(TDefinitions::class.java).createUnmarshaller()
            val reader = XMLInputFactory.newInstance().createXMLEventReader(inp)
            return (Processor(u.unmarshallerHandler, reader).run() as JAXBElement<TDefinitions>).value
        }

        internal fun loadStrict(inp: InputStream): TDefinitions {
            val u = JAXBContext.newInstance(TDefinitions::class.java).createUnmarshaller()
            return (u.unmarshal(inp) as JAXBElement<TDefinitions>).value
        }

        internal fun save(def: TDefinitions, out: OutputStream) {
            val wrap = JAXBElement<TDefinitions>(
                QName("http://www.omg.org/spec/BPMN/20100524/MODEL", "definitions"),
                def.javaClass,
                def
            )
            val writer = XMLOutputFactory.newInstance().createXMLEventWriter(out)
            JAXBContext.newInstance(TDefinitions::class.java).createMarshaller().marshal(wrap, writer)
        }
    }
}