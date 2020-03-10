package processm.core.models.causalnet

fun causalnet(init: CausalNetDSL.() -> Unit): MutableModel {
    val modelDSL = CausalNetDSL()
    modelDSL.init()
    return modelDSL.result()
}

class SplitsDSL(val base: CausalNetDSL, val source: Node) {
    infix fun or(target: Node): SplitsDSL {
        return this or setOf(target)
    }

    infix fun or(targets: Collection<Node>): SplitsDSL {
        base.splits.add(Split(targets.map { target -> Dependency(source, target) }.toSet()))
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

class CausalNetDSL {
    internal val joins = ArrayList<Join>()
    internal val splits = ArrayList<Split>()
    var start: Node = Node("start", special = true)
    var end: Node = Node("end", special = true)

    infix fun Set<Node>.join(target: Node) {
        this@CausalNetDSL.joins.add(Join(this.map { source -> Dependency(source, target) }.toSet()))
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


    fun result(): MutableModel {
        val model = MutableModel(start, end)
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
