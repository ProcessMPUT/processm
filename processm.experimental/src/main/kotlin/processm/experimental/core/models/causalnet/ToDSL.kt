package processm.experimental.core.models.causalnet

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node

/**
 * Export a [CausalNet] to Kotlin code compatible with [processm.core.models.causalnet.CausalNetDSL]
 *
 * @see [processm.core.models.causalnet.causalnet]
 */
fun CausalNet.toDSL(): String {
    fun str(s: String) = '"' + s.map {
        if (it in ' '..'\u007f' && it != '"')
            return@map it
        else {
            var hex = it.code.toString(16)
            require(hex.length <= 4)
            while (hex.length < 4)
                hex = "0$hex"
            return@map "\\u$hex"
        }
    }.joinToString("") + '"'

    fun node(n: Node): String {
        if (n == start)
            return "start"
        if (n == end)
            return "end"
        return if (n.instanceId.isBlank() && !n.isSilent)
            "Node(${str(n.activity)})"
        else
            "Node(${str(n.activity)}, ${str(n.instanceId)}, ${n.isSilent})"
    }

    var result = ""
    result += "start = Node(${str(start.activity)}, ${str(start.instanceId)}, ${start.isSilent})\n"
    result += "end = Node(${str(end.activity)}, ${str(end.instanceId)}, ${end.isSilent})\n"
    for (node in instances.sortedBy { it.activity }) {
        val njoins = this.joins[node].orEmpty().flatMap { it.dependencies }.size
        if (njoins > 0) {
            val joins = joins[node].orEmpty()
                .joinToString(separator = " or ") { join -> join.sources.joinToString(separator = "+") { node(it) } }
            if (njoins > 1)
                result += "$joins join ${node(node)}\n"
            else
                result += "$joins joins ${node(node)}\n"
        }
        if (splits[node].orEmpty().flatMap { it.dependencies }.isNotEmpty()) {
            val splits = splits[node].orEmpty().joinToString(separator = " or ") { split -> split.targets.joinToString(separator = "+") { node(it) } }
            result += "${node(node)} splits $splits\n"
        }
    }
    return result
}
