package processm.core.models.bpmn

import processm.core.models.bpmn.jaxb.TDefinitions
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory


sealed class BPMNXMLService {
    companion object {
        internal fun load(inp: InputStream): TDefinitions {
            val u = JAXBContext.newInstance(TDefinitions::class.java).createUnmarshaller();
            val reader = XMLInputFactory.newInstance().createXMLEventReader(inp)
            return (u.unmarshal(reader) as JAXBElement<TDefinitions>).value
        }

        internal fun save(def: TDefinitions, out: OutputStream) {
            val writer = XMLOutputFactory.newInstance().createXMLEventWriter(out)
            JAXBContext.newInstance(TDefinitions::class.java).createMarshaller().marshal(def, writer)
        }
    }
}