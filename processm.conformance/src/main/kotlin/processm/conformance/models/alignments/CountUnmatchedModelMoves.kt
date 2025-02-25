package processm.conformance.models.alignments

import com.carrotsearch.hppc.ObjectByteHashMap
import com.carrotsearch.hppc.ObjectByteMap
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.CausalNetState
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import processm.helpers.mapToSet
import java.util.*
import kotlin.math.max

/**
 * A base interface for counting necessary skip moves in the log
 */
interface CountUnmatchedModelMoves {
    /**
     * Called before each new trace
     */
    fun reset() {
    }

    /**
     * Number of skip moves in the log from [startIndex] onwards, given the current state of the model [prevProcessState] and aggregated [nEvents]
     */
    fun compute(
        startIndex: Int,
        nEvents: List<Map<String?, Int>>,
        prevProcessState: ProcessModelState,
        curActivity: Activity?
    ): Int
}

/**
 * [CountUnmatchedModelMoves] for [CausalNet]s
 */
class CountUnmatchedCausalNetMoves(val model: CausalNet) : CountUnmatchedModelMoves {
    private val minFutureExecutions = ThreadLocal.withInitial { ObjectByteHashMap<String>() }

    override fun compute(
        startIndex: Int,
        nEvents: List<Map<String?, Int>>,
        prevProcessState: ProcessModelState,
        curActivity: Activity?
    ): Int {
        prevProcessState as CausalNetState
        curActivity as DecoupledNodeExecution?
        val nEvents = if (startIndex < nEvents.size) nEvents[startIndex] else emptyMap<String?, Int>()
        // The maximum over the number of tokens on all incoming dependencies for an activity.
        // As each token must be consumed and a single execution may consume at most one token from the activity,
        // this is the same as the minimal number of pending executions for the activity.
        val minFutureExecutions = this.minFutureExecutions.get()
        if (prevProcessState.isFresh) {
            if (curActivity !== null && curActivity.join === null) {
                val split = curActivity.split
                if (split !== null) {
                    var index = 0
                    while (index < split.targets.size) {
                        val target = split.targets[index++]
                        if (target.isSilent)
                            continue
                        minFutureExecutions.putOrAdd(target.name, 1, 0)
                    }
                }
            } else if (!model.start.isSilent) {
                minFutureExecutions.put(model.start.name, 1)
            }
        } else if (prevProcessState.isNotEmpty()) {
            for (e in prevProcessState.entrySet()) {
                var count = e.count
                if (curActivity?.join?.dependencies?.contains(e.element) == true) {
                    --count
                    // ignore tokens firing prevActivity and produce tokens created by prevActivity
                    val split = curActivity!!.split
                    if (split !== null) {
                        var index = 0
                        while (index < split.targets.size) {
                            val target = split.targets[index++]
                            if (target.isSilent)
                                continue
                            minFutureExecutions.putOrAdd(target.name, 1, 0)
                        }
                    }
                }

                if (e.element.target.isSilent)
                    continue

                minFutureExecutions.putOrMax(e.element.target.name, count)
            }
        }

        var sum = 0
        for (iter in minFutureExecutions.iterator()) {
            val e = nEvents[iter.key] ?: 0
            val diff = (iter.value - e)
            if (diff > 0)
                sum += diff
        }
        minFutureExecutions.clear()

        return sum
    }

    private fun <K> ObjectByteMap<K>.putOrMax(key: K, putValue: Byte) {
        val index = indexOf(key)
        if (index >= 0) {
            val old = indexGet(index)
            if (old < putValue)
                indexReplace(index, putValue)
        } else {
            indexInsert(index, key, putValue)
        }
    }
}

/**
 * [CountUnmatchedModelMoves] for [PetriNet]s
 */
class CountUnmatchedPetriNetMoves(val model: PetriNet) : CountUnmatchedModelMoves {

    private val consumentsCache = ThreadLocal.withInitial { HashMap<Pair<Place, Int>, Int>() }

    private val followingCache = ThreadLocal.withInitial { HashMap<Place, Set<Set<String>>>() }

    private fun following(place: Place): Set<Set<String>> {
        return followingCache.get().computeIfAbsent(place) { place ->
            val following = HashSet<Set<String>>()
            for (set in model.forwardSearch(place).mapToSet { set -> set.mapToSet { it.name } }.sortedBy { it.size }) {
                if (following.none { subset -> set.containsAll(subset) })
                    following.add(set)
            }
            return@computeIfAbsent following
        }
    }

    override fun reset() {
        consumentsCache.get().clear()
    }

    override fun compute(
        startIndex: Int,
        nEvents: List<Map<String?, Int>>,
        prevProcessState: ProcessModelState,
        curActivity: Activity?
    ): Int {
        prevProcessState as Marking
        curActivity as Transition?

        if (prevProcessState.isEmpty())
            return 0

        // Complete dealing with nonConsumable would require some complex algorithm.
        // Consider: ([[c],[e]]: 1, [[d],[e]]: 1). It is sufficient to model-skip over e to consume these two tokens.
        // Consider further: ([[c, e]]: 1, [[d, e]]: 1). Now one needs at least three model-skips: c, d, e (used twice)
        // ([[c, e]]: 2, [[d, e]]: 1) -> cceed - 5 model-skips
        // Underestimating: each set of sets is flattened and it is assumed that it is sufficient to execute a single activity from it to consume a token
        // ([c, e]: 2, [d, e]: 1) -> ee - 2 model-skips
        // Still not entirely clear how to deal with it, e.g., ([c, e]: 2, [d, e]: 1, [d, c]: 3) -> dcc - 3 model-skips seems to be an optimal solution
        // Underestimating further: computing unions of sets having at least one common element and assigning them the maximal value over all the original counters -> ([c, d, e]: 3).
        // At this point it is sufficient to sum the counters
        val nEvents = if (startIndex < nEvents.size) nEvents[startIndex] else emptyMap()
        val nonConsumable = ArrayList<Pair<Set<Set<String>>, Int>>()
        val consumentsCache = this.consumentsCache.get()
        for ((place, counter) in prevProcessState) {
            assert(!counter.isEmpty())
            var tokenCount = counter.size
            if (curActivity?.inPlaces?.contains(place) == true) {
                if (--tokenCount == 0)
                    continue
            }

            if (model.placeToFollowingTransition[place]?.any { it.isSilent } == true)
                continue

            val following = this.following(place)
            val consuments = consumentsCache.computeIfAbsent(place to startIndex) {
                following.sumOf { set -> set.minOf { nEvents[it] ?: 0 } }
            }
            if (tokenCount > consuments && following.isNotEmpty())
                nonConsumable.add(following to tokenCount - consuments)
        }
        if (nonConsumable.isEmpty())
            return 0
        var disjointSum = 0
        // use LinkedList to enable efficient removal from the middle
        val flat = nonConsumable.mapNotNullTo(LinkedList()) {
            val set = it.first.flatMapTo(HashSet()) { it }
            if (set.isNotEmpty())
                return@mapNotNullTo set to it.second
            else
                return@mapNotNullTo null
        }
        val set = HashSet<String>()
        while (flat.isNotEmpty()) {
            set.clear()
            var ctr = 0
            val i = flat.iterator()
            while (i.hasNext()) {
                val e = i.next()
                if (set.isEmpty() || e.first.any { it in set }) {
                    set.addAll(e.first)
                    ctr = max(ctr, e.second)
                    i.remove()
                }
            }
            assert(set.isNotEmpty())
            assert(ctr > 0)
            disjointSum += ctr
        }
        return disjointSum
    }
}
