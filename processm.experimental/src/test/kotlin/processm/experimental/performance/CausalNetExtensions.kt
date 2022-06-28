package processm.experimental.performance

import processm.core.models.causalnet.Binding
import processm.core.models.causalnet.CausalNet

fun CausalNet.toPython(): String {
    fun wrap(obj: Any): String {
        val text = obj.toString()
        require('\'' !in text)
        return "'%s'".format(text.replace("\n", "\\n"))
    }

    val result = StringBuilder()
    fun bindings(bindings: Iterable<Binding>, name: String) {
        result.append(name)
        result.appendLine(" = [")
        result.append(bindings.joinToString(separator = ",\n") { b ->
            "    [" + b.dependencies.joinToString(separator = ", ") {
                "(%s, %s)".format(
                    wrap(it.source),
                    wrap(it.target)
                )
            } + "]"
        })
        result.appendLine()
        result.appendLine("]")
    }
    result.append("nodes = [")
    result.append(this.instances.joinToString(separator = ", ", transform = ::wrap))
    result.appendLine("]")
    bindings(joins.values.flatten(), "joins")
    bindings(splits.values.flatten(), "splits")
    return result.toString()
}
