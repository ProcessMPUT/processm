package processm.experimental.performance.perfectaligner

import processm.core.models.causalnet.*
import processm.experimental.helpers.BitIntSet

class IntCausalNet(val base: CausalNet) {
    internal val nodeEncoder = HashMap<Node, Int>()
    internal val depEncoder = HashMap<Dependency, Int>()
    internal val dep2Source = ArrayList<Int>()
    internal val dep2Target = ArrayList<Int>()
    internal val incomingDeps = ArrayList<BitIntSet>() // node -> incoming dependencies
    internal val joins = ArrayList<IntRange>() // node -> indices in flatJoins
    internal val flatJoins = ArrayList<BitIntSet>()
    internal val joinsDecoder = ArrayList<Join>()   // same order as flatJoins
    internal val dep2joins = ArrayList<ArrayList<Int>>() // dependency -> indices in flatJoins
    internal val join2Target = ArrayList<Int>() // join (same order as flatJoins) -> join's target node
    internal val splits = ArrayList<IntRange>() // node -> indices in flatSplits
    internal val flatSplits = ArrayList<BitIntSet>()
    internal val splitsDecoder = ArrayList<Split>() // same order as flatSplits
    internal val split2Joins =
        ArrayList<IntArray>() // split -> joins (as indices in flatJoins) containing at least one dependency of the split
    internal val nNodes: Int = base.instances.size
    internal val nDeps: Int = base.dependencies.size
    internal val nJoins: Int
        get() = flatJoins.size
    internal val start: Int
    internal val end: Int

    init {
        for (n in base.instances) {
            nodeEncoder[n] = nodeEncoder.size
            incomingDeps.add(BitIntSet(nDeps))
        }
        start = nodeEncoder[base.start]!!
        end = nodeEncoder[base.end]!!
        for (dep in base.dependencies) {
            // nodeEncoder[dep.source]!!*nNodes + nodeEncoder[dep.target]!!
            val depIdx = dep2Target.size
            depEncoder[dep] = depIdx
            dep2Source.add(nodeEncoder[dep.source]!!)
            val target = nodeEncoder[dep.target]!!
            dep2Target.add(target)
            dep2joins.add(ArrayList())
            incomingDeps[target].add(depIdx)
        }
        joins.ensureCapacity(nNodes)
        for (i in 0 until nNodes)
            this.joins.add(IntRange.EMPTY)
        for ((dst, joins) in base.joins) {
            val start = this.flatJoins.size
            for (join in joins) {
                val joinIdx = this.flatJoins.size
                this.flatJoins.add(join.dependencies.mapTo(BitIntSet(nDeps)) { depEncoder[it]!! })
                this.joinsDecoder.add(join)
                this.join2Target.add(nodeEncoder[join.target]!!)
                for (dep in join.dependencies)
                    this.dep2joins[depEncoder[dep]!!].add(joinIdx)
            }
            this.joins[nodeEncoder[dst]!!] = IntRange(start, this.flatJoins.size - 1)
        }
        splits.ensureCapacity(nNodes)
        for (i in 0 until nNodes)
            this.splits.add(IntRange.EMPTY)
        for ((src, splits) in base.splits) {
            val result = ArrayList<Int>()
            val start = this.flatSplits.size
            for (split in splits) {
                val deps = split.dependencies.mapTo(BitIntSet(nDeps)) { depEncoder[it]!! }
                this.flatSplits.add(deps)
                this.splitsDecoder.add(split)
//                this.split2Joins.add(deps.flatMapTo(BitIntSet(nJoins)) { dep -> dep2joins[dep]!! })
                val tmp = deps.flatMapTo(ArrayList()) { dep -> dep2joins[dep]!! }
                this.split2Joins.add(tmp.toIntArray())
            }
            this.splits[nodeEncoder[src]!!] = IntRange(start, this.flatSplits.size - 1)
        }
    }

}

