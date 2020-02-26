package processm.core.models.bpmn.infrastructure

import processm.core.models.bpmn.BPMNDiagram
import processm.core.models.bpmn.RootElement
import processm.core.models.bpmn.URI
import processm.core.models.bpmn.foundation.BaseElement
import processm.core.models.bpmn.foundation.Extension

//TODO should targetNamespace be an URI
class Definition(id: String, val name: String, val targetNamespace: String) : BaseElement(id) {
    var expressionLanguage = URI("http://www.w3.org/1999/XPath")
        internal set
    var typeLanguage = URI("http://www.w3.org/2001/XMLSchema")
        internal set
    val rootElements: List<RootElement> = ArrayList()
    val diagrams: List<BPMNDiagram> = ArrayList()
    val imports: List<Import> = ArrayList()
    val extensions: List<Extension> = ArrayList()
    val relationship: List<Relationship> = ArrayList()
    var exporter: String? = null
        internal set
    var exporterVersion: String? = null
        internal set
}