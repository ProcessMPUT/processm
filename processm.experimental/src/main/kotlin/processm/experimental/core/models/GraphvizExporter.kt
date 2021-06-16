package processm.experimental.core.models.causalnet

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Dependency
import processm.core.models.commons.Activity
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place

/**
 * Export a [PetriNet] to a Graphviz graph description
 */
fun PetriNet.toGraphviz(): String {
    val result = StringBuilder()
    result.appendLine("digraph g {")
    val p2node = HashMap<Place, String>()
    for (p in places) {
        val name = "p${p2node.size}"
        p2node[p] = name
        result.appendLine("$name [shape=circle, label=\"\"];")
    }
    var ctr = 0
    for (t in transitions) {
        val name = "t$ctr"
        ctr++
        result.appendLine("$name [shape=box, label=\"${t.name}\"];")
        for (p in t.inPlaces)
            result.appendLine("${p2node[p]!!} -> $name;")
        for (p in t.outPlaces)
            result.appendLine("$name -> ${p2node[p]!!};")
    }
    result.appendLine("}")
    return result.toString()
}

/**
 * Export a [CausalNet] to a Graphviz graph description. Drawing bindings ([drawBindgings]`=true`) is currently not implemented.
 */
fun CausalNet.toGraphviz(drawBindgings: Boolean = false): String {
    val a2node = HashMap<Activity, String>()
    val result = StringBuilder()
    result.appendLine("digraph g {")
    for (a in activities) {
        val name = "a${a2node.size}"
        a2node[a] = name
        result.append(name)
        result.append(" [label=\"")
        result.append(a.name)
        result.append("\"")
        if (a.special)
            result.append(",fontcolor=red")
        result.appendLine("];")
    }
    if (drawBindgings) {
        TODO()
        val d2split = HashMap<Dependency, ArrayList<String>>()
        var ctr = 0
        for (split in this.splits.values.flatten()) {
            val splitNodes = ArrayList<String>()
            for (d in split.dependencies) {
                val node = "split$ctr"
                ctr++
                d2split.computeIfAbsent(d) { ArrayList() }.add(node)
                splitNodes.add(node)
            }
        }
    } else {
        for (d in dependencies) {
            result.append(a2node[d.source]!!)
            result.append(" -> ")
            result.append(a2node[d.target]!!)
            result.appendLine(";")
        }
    }
    result.appendLine("}")
    return result.toString()
}