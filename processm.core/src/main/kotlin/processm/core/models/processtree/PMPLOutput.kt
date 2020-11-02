package processm.core.models.processtree

import javax.xml.stream.XMLStreamWriter

class PMPLOutput(private val output: XMLStreamWriter) {
    fun write(tree: ProcessTree) {
        output.writeStartDocument("UTF-8", "1.0")
        // Start ptml
        output.writeStartElement("ptml")
        // Start processTree
        output.writeStartElement("processTree")
        output.writeAttribute("id", tree.hashCode().toString())
        output.writeAttribute("name", tree.hashCode().toString())
        output.writeAttribute("root", tree.root.hashCode().toString())

        loop(tree.root!!, this::writeNodeDefinition)
        loop(tree.root!!, this::writeHierarchy)

        // End processTree
        output.writeEndElement()

        // End ptml
        output.writeEndDocument()
    }

    private fun loop(node: Node, function: (Node) -> Unit) {
        function(node)
        node.children.forEach { loop(it, function) }
    }

    private fun writeNodeDefinition(node: Node) {
        val nodeHashCode = node.hashCode().toString()
        val name = when (node) {
            is SilentActivity -> "τ"
            is ProcessTreeActivity -> node.name
            else -> ""
        }
        val element = when (node) {
            is SilentActivity -> "automaticTask"
            is ProcessTreeActivity -> "manualTask"
            is Sequence -> "sequence"
            is Exclusive -> "xor"
            is Parallel -> "and"
            is RedoLoop -> "xorLoop"
            else -> error("Invalid node type")
        }

        output.writeEmptyElement(element)
        output.writeAttribute("id", nodeHashCode)
        output.writeAttribute("name", name)
    }

    private fun writeHierarchy(node: Node) {
        if (node.parent !== null) {
            val parentHashCode = node.parent.hashCode().toString()
            val nodeHashCode = node.hashCode().toString()

            // Add extra xor node required by PMPL converter
            if (node.parent is RedoLoop) {
                if (node === node.parent!!.children.first()) {
                    // Extra XOR
                    output.writeEmptyElement("xor")
                    output.writeAttribute("id", "$parentHashCode-$parentHashCode")
                    output.writeAttribute("name", "")

                    // Add extra XOR to parent
                    output.writeEmptyElement("parentsNode")
                    output.writeAttribute("id", "assign-to-parent-$parentHashCode")
                    output.writeAttribute("sourceId", parentHashCode)
                    output.writeAttribute("targetId", "$parentHashCode-$parentHashCode")

                    // Add normal node
                    output.writeEmptyElement("parentsNode")
                    output.writeAttribute("id", nodeHashCode)
                    output.writeAttribute("sourceId", parentHashCode)
                    output.writeAttribute("targetId", nodeHashCode)
                } else {
                    // Add reference to extra XOR node
                    output.writeEmptyElement("parentsNode")
                    output.writeAttribute("id", "random-$nodeHashCode")
                    output.writeAttribute("sourceId", "$parentHashCode-$parentHashCode")
                    output.writeAttribute("targetId", nodeHashCode)
                }
            } else {
                output.writeEmptyElement("parentsNode")
                output.writeAttribute("id", nodeHashCode)
                output.writeAttribute("sourceId", parentHashCode)
                output.writeAttribute("targetId", nodeHashCode)
            }
        }
    }
}