package processm.core.models.bpmn

import java.io.InputStream
import processm.core.models.bpmn.jaxb.TDefinitions
import java.io.OutputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement

sealed class BPMNXMLService {
    companion object {
        internal fun load(inp: InputStream): TDefinitions {
            var tmp = JAXBContext.newInstance(TDefinitions::class.java).createUnmarshaller().unmarshal(inp) as JAXBElement<TDefinitions>
            return tmp.value
        }

        internal fun save(def: TDefinitions, out: OutputStream) {
            JAXBContext.newInstance(TDefinitions::class.java).createMarshaller().marshal(def, out)
        }
    }
}