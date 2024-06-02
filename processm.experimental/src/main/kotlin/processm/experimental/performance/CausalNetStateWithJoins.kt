package processm.experimental.performance

import processm.core.models.causalnet.*

open class CausalNetStateWithJoins : CausalNetStateImpl {
    val context: StateContext
    val activeJoins: Set<Join?>
        get() = joins
    private var joins: HashSet<Join?>
    private val activeDepsEnc = HashMap<Node, Long>()

    constructor(context: StateContext) : super() {
        this.context = context
        this.joins = hashSetOf(null)
    }

    constructor(stateBefore: CausalNetStateWithJoins) : super(stateBefore) {
        this.context = stateBefore.context
        this.joins = stateBefore.joins
        this.activeDepsEnc.putAll(stateBefore.activeDepsEnc)
    }

    private fun executeJoin(join: Join?): List<Dependency> {
        if (join != null) {
            val removedDeps = ArrayList<Dependency>()
            //    check(this.containsAll(join.dependencies)) { "It is impossible to execute this join in the current state" }
            for (d in join.dependencies) {
                val removed = this.remove(d, 1)
                if (removed)
                    removedDeps.add(d)
            }
            return removedDeps
        } else
            return emptyList()
    }

    private fun executeSplit(split: Split?): List<Dependency> {
        if (split != null) {
            val addedDeps = ArrayList<Dependency>()
            for (dep in split.dependencies) {
                val added = this.add(dep, 1)
                if (added)
                    addedDeps.add(dep)
            }
            return addedDeps
        } else
            return emptyList()
    }

    private fun retrieve(target: Node): Pair<Map<Dependency, Long>, List<Pair<Join, Long>>> =
        context.cache.computeIfAbsent(target) {
            var ctr = 0
            val d2b = context.model.incoming[target].orEmpty().associateWith { 1L shl (ctr++) }
            check(ctr <= Long.SIZE_BITS)
            val j2b = context.model.joins[target].orEmpty().map { join ->
                var enc = 0L
                for (d in join.dependencies)
                    enc = enc or (d2b[d] ?: 0L)
                return@map join to enc
            }
            return@computeIfAbsent d2b to j2b
        }

    override fun execute(join: Join?, split: Split?) {
        val removedDeps = executeJoin(join)
        val addedDeps = executeSplit(split)
        var newJoins = HashSet<Join?>()
        //val uniqueSet = uniqueSet().groupBy { it.target }
        //assert((join == null) == (joins == setOf(null)))
        if (join != null) {
            joins.filterTo(newJoins) { j -> j != null && removedDeps.all { d -> !j.contains(d) } }
            val (d2b, j2b) = retrieve(join.target)
            activeDepsEnc.compute(join.target) { _, v ->
                var removedEnc = 0L
                for (dep in removedDeps)
                    removedEnc = removedEnc or d2b[dep]!!
                return@compute (v ?: 0L) and (removedEnc.inv())
            }
            /*
            var v = activeDepsEnc[join.target]?:0
            for(dep in removedDeps)
                v = v and d2b[dep]!!.inv()
            activeDepsEnc[join.target] = v
             */
        } else {
            newJoins = HashSet(joins)
            newJoins.remove(null)
        }
        /*
        for ((target, deps) in addedDeps.groupBy { it.target })
            for (join in model.joins[target].orEmpty()) {
                if (/*deps.any { it in join } &&*/ uniqueSet[target].orEmpty().containsAll(join.dependencies))
                    newJoins.add(join)
            }
         */
        for ((target, deps) in addedDeps.groupBy { it.target }) {
            val (d2b, j2b) = retrieve(target)
            val avail = activeDepsEnc.compute(target) { _, old ->
                var v = old ?: 0L
                for (dep in deps)
                    v = v or d2b[dep]!!
                return@compute v
            }!!
            /*
            var avail2 = 0L
            for (d in uniqueSet[target].orEmpty())
                avail2 = avail or d2b[d]!!
            assert(avail == avail2)
             */
            for (e in j2b) {
                if (avail and e.second == e.second) {
                    // assert(uniqueSet[target].orEmpty().containsAll(e.key.dependencies))
                    newJoins.add(e.first)
                }
            }
        }
        joins = newJoins
    }
}
