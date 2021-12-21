package processm.experimental.onlinehmpaper

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import java.io.OutputStream
import java.io.PrintStream

class Generator(val out: PrintStream) {
    fun start(id: String) {
        out.println(
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <pnml>
              <net id="$id" type="http://www.pnml.org/version-2009/grammar/pnmlcoremodel">
                <page id="n0">
        """.trimIndent()
        )
    }

    fun transition(id: String, label: String?) {
        out.println("<transition id=\"$id\">")
        if (label != null)
            out.println("<name><text>$label</text></name>")
        out.println("</transition>")
    }

    fun place(id: String, initial: Boolean = false) {
        val initialText = if (initial) "<initialMarking><text>1</text></initialMarking>" else ""
        out.println("<place id=\"$id\"><name><text>$id</text></name>$initialText</place>")
    }

    fun end(endId: String) {
        out.println("</page>")
        //out.println("<initialmarkings><marking><place idref=\"$startId\"><text>1</text></place></marking></initialmarkings>")
        out.println("<finalmarkings><marking><place idref=\"$endId\"><text>1</text></place></marking></finalmarkings>")
        out.println("</net></pnml>")
    }

    private var ctr = 0

    fun arc(source: String, target: String) {
        out.println("<arc id=\"arc$ctr\" source=\"$source\" target=\"$target\"/>")
        ctr += 1
    }
}

fun CausalNet.toPM4PY(out: OutputStream) {
    // activity -> transition
    // binding -> transition
    // dependency -> place between two bindings
    // + auxiliary places to enforce selecting a single binding for each node
    val gen = Generator(PrintStream(out))
    gen.start("net1")
    val node2id = HashMap<Node, String>()
    for (node in this.instances) {
        val id =
            node.name //not using anything other than name to ensure that anything depending on names can use the resulting model as well
        gen.transition(id, id)
        node2id[node] = id
    }
    check(node2id.values.toSet().size == node2id.values.size) { "There are duplicate activity names in the model." }
    val dep2id = HashMap<Dependency, String>()
    for (dep in this.dependencies) {
        val id = "${node2id[dep.source]!!}->${node2id[dep.target]!!}"
        gen.place(id)
        dep2id[dep] = id
    }
    for ((src, splits) in this.splits.entries) {
        val aux = "split selector for ${src.name}"
        gen.place(aux)
        gen.arc(node2id[src]!!, aux)
        for (split in splits) {
            val splitId = "{${node2id[split.source]!!} -> ${
                split.targets.joinToString(
                    separator = ", ",
                    prefix = "[",
                    postfix = "]"
                ) { node2id[it]!! }
            }}"
            gen.transition(splitId, null)
            gen.arc(aux, splitId)
            for (dep in split.dependencies)
                gen.arc(splitId, dep2id[dep]!!)
        }
    }
    for ((dst, joins) in this.joins.entries) {
        val aux = "join selector for ${dst.name}"
        gen.place(aux)
        gen.arc(aux, node2id[dst]!!)
        for (join in joins) {
            val joinId = "{${
                join.sources.joinToString(
                    separator = ", ",
                    prefix = "[",
                    postfix = "]"
                ) { node2id[it]!! }
            } -> ${node2id[join.target]}}"
            gen.transition(joinId, null)
            gen.arc(joinId, aux)
            for (dep in join.dependencies)
                gen.arc(dep2id[dep]!!, joinId)
        }
    }
    val startPlace = "->${this.start.name}"
    gen.place(startPlace, true)
    gen.arc(startPlace, node2id[this.start]!!)
    val endPlace = "${this.end.name}->"
    gen.place(endPlace)
    gen.arc(node2id[this.end]!!, endPlace)
    gen.end(endPlace)
}