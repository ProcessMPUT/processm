package processm.core.models.causalnet

import processm.core.helpers.mapToSet

/**
 * Constructs a Causal Net using a Domain Specific Language (DSL) defined by [CausalNetDSL].
 *
 * The idea is to specify only splits and joins (and, optionally, start and end), and from that to infer automatically
 * all the nodes and dependencies present in the model. However, apart from this,  the DSL does not perform any sort of
 * guessing, validation or conformance checking, so it is entirely possible to build an incorrect causal net. As the main
 * purpose of this DSL is to make writing test code easier, it is an intended behaviour to allow for constructing invalid
 * causal nets.
 *
 * ### Example:
 * In the following code, `a`, `b1`, `b2`, `c`, `d`, `e` are objects of the class `Node`.
 * The resulting causal net is such that a is always followed by `b1` and/or `b2`, they are followed by
 * a single execution of `c`, then `d` is executed only if both `b1` and `b2` were executed, and finally `e` is executed.
 * ```
 * val model = causalnet {
 *     start = a
 *     end = e
 *     a splits b1 or b2 or b1 + b2
 *     b1 splits c + e or c + d
 *     b2 splits c + e or c + d
 *     c splits d or e
 *     d splits e
 *     a joins b1
 *     a joins b2
 *     b1 or b2 or b1 + b2 join c
 *     b1 + b2 + c join d
 *     c + b1 or c + b2 or d join e
 * }
 * ```
 *
 * ### Grammar
 *
 * The following EBNF grammar defines the language a non-terminal `CausalNet` which should be the sole item enclosed in
 * in the code block and where a terminal `Node` an object of the class [Node].
 * The language is still a Kotlin code
 * ```
 * CausalNet = (Start | End | Split | Join)*
 * Start = "start" "=" Node
 * End = "end" "=" Node
 * Split = Node "splits" Dependencies
 * Join = (Node "joins" Node) | (Dependencies "join" Node)
 * Dependency = Node | (Node "+" Dependency)
 * Dependencies = Dependency | (Dependency "or" Dependencies)
 * ```
 * Two forms of defining a join are introduced to make the language more similar to English's Present Simple.
 * If one does not define `start` and/or `end`, default values are provided.
 * These symbols are valid nodes and can be used wherever `Node` is needed.
 * The order of defining splits and joins does not matter, but `Start` and `End` are assignments to a variable
 * and thus, if one wants to use `start` and `end` as nodes, the assignment must go before the first usage.
 */
fun causalnet(init: CausalNetDSL.() -> Unit): MutableCausalNet {
    val modelDSL = CausalNetDSL()
    modelDSL.init()
    return modelDSL.result()
}

class SplitsDSL(val base: CausalNetDSL, val source: Node) {
    infix fun or(target: Node): SplitsDSL {
        return this or setOf(target)
    }

    infix fun or(targets: Collection<Node>): SplitsDSL {
        base.splits.add(Split(targets.mapToSet { target -> Dependency(source, target) }))
        return this
    }
}

class SetOfNodes(val base: Set<Node>) : Set<Node> by base {
    infix fun or(other: Node): SetOfSetOfNodes {
        return SetOfSetOfNodes(setOf(this)) or other
    }

    infix fun or(other: Set<Node>): SetOfSetOfNodes {
        return SetOfSetOfNodes(setOf(this)) or other
    }
}

class SetOfSetOfNodes(val base: Set<Set<Node>>) : Set<Set<Node>> by base {
    infix fun or(other: Node): SetOfSetOfNodes {
        return this or SetOfNodes(setOf(other))
    }

    infix fun or(other: Set<Node>): SetOfSetOfNodes {
        return SetOfSetOfNodes(base + setOf(other))
    }
}

/**
 * @see [causalnet]
 */
class CausalNetDSL {
    internal val joins = ArrayList<Join>()
    internal val splits = ArrayList<Split>()
    var start: Node = Node("start", special = true)
    var end: Node = Node("end", special = true)

    infix fun Set<Node>.join(target: Node) {
        this@CausalNetDSL.joins.add(Join(this.mapToSet { source -> Dependency(source, target) }))
    }

    infix fun SetOfSetOfNodes.join(target: Node) {
        this.forEach { sources -> sources.join(target) }
    }

    infix fun Node.joins(target: Node) {
        this@CausalNetDSL.joins.add(Join(setOf(Dependency(this, target))))
    }

    infix fun Node.splits(targets: Collection<Node>): SplitsDSL {
        return SplitsDSL(this@CausalNetDSL, this) or targets
    }

    infix fun Node.splits(target: Node): SplitsDSL {
        return SplitsDSL(this@CausalNetDSL, this) or target
    }

    operator fun Node.plus(other: Node): SetOfNodes {
        return SetOfNodes(setOf(this, other))
    }

    infix fun Node.or(other: Node): SetOfSetOfNodes {
        return SetOfNodes(setOf(this)) or other
    }

    infix fun Node.or(other: Set<Node>): SetOfSetOfNodes {
        return SetOfNodes(other) or this
    }

    infix fun Node.or(other: SetOfSetOfNodes): SetOfSetOfNodes {
        return other or this
    }

    infix fun Set<Node>.or(other: Set<Node>): SetOfSetOfNodes {
        return SetOfNodes(this) or SetOfNodes(other)
    }

    infix fun Set<Node>.or(other: Node): SetOfSetOfNodes {
        return this or setOf(other)
    }


    fun result(): MutableCausalNet {
        val model = MutableCausalNet(start, end)
        (joins.map { j -> j.dependencies } + splits.map { s -> s.dependencies })
            .flatten()
            .forEach { dep ->
                model.addInstance(dep.source)
                model.addInstance(dep.target)
                model.addDependency(dep)
            }
        joins.forEach { model.addJoin(it) }
        splits.forEach { model.addSplit(it) }
        return model
    }
}
