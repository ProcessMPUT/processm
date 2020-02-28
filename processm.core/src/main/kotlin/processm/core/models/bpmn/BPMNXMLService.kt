package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TDefinitions
import java.io.InputStream
import java.io.OutputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
import javax.xml.stream.XMLEventFactory
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.events.Namespace
import javax.xml.stream.events.XMLEvent


sealed class BPMNXMLService {

    private class NamespaceAddingEventReader(val base: XMLEventReader) : XMLEventReader by base {

        private val factory = XMLEventFactory.newInstance();

        private fun process(event: XMLEvent): XMLEvent {
            if (event.isStartElement) {
                val orig = event.asStartElement()
                if (orig.getNamespaceURI("bpmn") == null) {
                    val namespaces = ArrayList<Namespace>()
                    (orig.namespaces as Iterator<Namespace>).forEach { n -> namespaces.add(n) }
                    namespaces.add(factory.createNamespace("bpmn", "http://www.omg.org/spec/BPMN/20100524/MODEL"))
                    return factory.createStartElement(orig.name, orig.attributes, namespaces.iterator())
                }
            }
            return event
        }

        override fun nextEvent(): XMLEvent {
            return process(base.nextEvent())
        }

        override fun peek(): XMLEvent {
            return process(base.peek())
        }
    }

    companion object {
        internal fun load(inp: InputStream): TDefinitions {
            val u = JAXBContext.newInstance(TDefinitions::class.java).createUnmarshaller();
            val reader = NamespaceAddingEventReader(XMLInputFactory.newInstance().createXMLEventReader(inp))
            return (u.unmarshal(reader) as JAXBElement<TDefinitions>).value
        }

        internal fun save(def: TDefinitions, out: OutputStream) {
            val writer = XMLOutputFactory.newInstance().createXMLEventWriter(out)
            JAXBContext.newInstance(TDefinitions::class.java).createMarshaller().marshal(def, writer)
        }
    }
}