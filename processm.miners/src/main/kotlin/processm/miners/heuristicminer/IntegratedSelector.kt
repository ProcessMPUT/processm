package processm.miners.heuristicminer

import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Split

class IntegratedSelector(private val minSupport: Int) {
    protected val joinCounter = Counter<Join>()
    protected val splitCounter = Counter<Split>()

    fun addJoins(joins: Collection<Join>) {
        joins.forEach { binding ->
            println("Voting for " + binding)
            joinCounter.inc(binding)
        }
    }

    fun addSplits(splits: Collection<Split>) {
        splits.forEach { binding ->
            println("Voting for " + binding)
            splitCounter.inc(binding)
        }
    }

    fun best(available: Set<Dependency>): Pair<Set<Join>, Set<Split>> {
        println("AVAIL " + available.map { dep -> dep.source.activity to dep.target.activity })
        val localSplitCounter = Counter(splitCounter)
        val localJoinCounter = Counter(joinCounter)
        val bestSplits = localSplitCounter.filterValues { it >= minSupport }.keys
        val bestJoins = localJoinCounter.filterValues { it >= minSupport }.keys
        val invalidBestDependencies = (bestSplits + bestJoins)
            .flatMap { it.dependencies }
            .filter { !available.contains(it) }
            .toSet()
        if (invalidBestDependencies.isEmpty())
            return bestJoins to bestSplits
        val bySource = available.groupBy { it.source }
        val byTarget = available.groupBy { it.target }

        //przepisywanie z sekwencji ABCE na diament A (B || C) E
        for (invalidDependency in invalidBestDependencies) {
            val (b, c) = invalidDependency
            val allA = bySource
                .filterValues { deps ->
                    deps.map { it.target }.containsAll(setOf(invalidDependency.source, invalidDependency.target))
                }
                .keys
            val allE = byTarget
                .filterValues { deps ->
                    deps.map { it.source }.containsAll(setOf(invalidDependency.source, invalidDependency.target))
                }
                .keys
            if (allA.isEmpty() || allE.isEmpty())
                return setOf<Join>() to setOf()
            val a = allA.single()
            val e = allE.single()
            val ab = Dependency(a, b)
            val ac = Dependency(a, c)
            val bc = invalidDependency
            val be = Dependency(b, e)
            val ce = Dependency(c, e)
            val x = splitCounter.getValue(Split(setOf(bc)))
            val y = joinCounter.getValue(Join(setOf(bc)))
            assert(x == y)
            localSplitCounter.dec(Split(setOf(ab)), x)
            localSplitCounter.inc(Split(setOf(ab, ac)), x)
            localSplitCounter.inc(Split(setOf(be)), x)
            localJoinCounter.inc(Join(setOf(ac)), x)
            localJoinCounter.dec(Join(setOf(ce)), x)
            localJoinCounter.inc(Join(setOf(be, ce)), x)
            localSplitCounter.remove(Split(setOf(bc)))
            localJoinCounter.remove(Join(setOf(bc)))
        }
        localSplitCounter
            .forEach { (k, v) -> println("${k.source.activity to k.targets.map { it.activity }}: $v/" + splitCounter[k]) }
        localJoinCounter
            .forEach { (k, v) -> println("${k.sources.map { it.activity } to k.target.activity}: ${joinCounter[k]} ~~> $v") }
        val newBestSplits = localSplitCounter.filterValues { it >= minSupport }.keys
        val newBestJoins = localJoinCounter.filterValues { it >= minSupport }.keys
        assert(available.containsAll(newBestSplits.flatMap { it.dependencies }))
        assert(available.containsAll(newBestJoins.flatMap { it.dependencies }))
        return newBestJoins to newBestSplits
    }
}