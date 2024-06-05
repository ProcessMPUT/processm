package processm.conformance.models.alignments

import com.carrotsearch.hppc.ObjectIntHashMap
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.CausalNetState
import processm.core.models.commons.ProcessModelState
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
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
    fun compute(startIndex: Int, nEvents: List<Map<String?, Int>>, prevProcessState: ProcessModelState): Int
}

/**
 * [CountUnmatchedModelMoves] for [CausalNet]s
 */
class CountUnmatchedCausalNetMoves(val model: CausalNet) : CountUnmatchedModelMoves {
    private val minFutureExecutions = ThreadLocal.withInitial { ObjectIntHashMap<String>() }

    override fun compute(startIndex: Int, nEvents: List<Map<String?, Int>>, prevProcessState: ProcessModelState): Int {
        prevProcessState as CausalNetState
        val nEvents = if (startIndex < nEvents.size) nEvents[startIndex] else emptyMap<String?, Int>()
        // The maximum over the number of tokens on all incoming dependencies for an activity.
        // As each token must be consumed and a single execution may consume at most one token from the activity,
        // this is the same as the minimal number of pending executions for the activity.
        val minFutureExecutions = this.minFutureExecutions.get()
        if (prevProcessState.isFresh) {
            minFutureExecutions.put(model.start.name, 1)
        } else {
            for (e in prevProcessState.entrySet()) {
                if (!e.element.target.isSilent) {
                    val old = minFutureExecutions.put(e.element.target.name, e.count.toInt())
                    if (old > e.count)
                        minFutureExecutions.put(e.element.target.name, old)
                }
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
}

/**
 * [CountUnmatchedModelMoves] for [PetriNet]s
 */
class CountUnmatchedPetriNetMoves(val model: PetriNet) : CountUnmatchedModelMoves {

    private val consumentsCache = HashMap<Pair<Place, Int>, Int>()

    private val followingCache: HashMap<Place, Set<Set<String>>> = HashMap()

    private fun following(place: Place): Set<Set<String>> {
        return followingCache.computeIfAbsent(place) { place ->
            val following = HashSet<Set<String>>()
            for (set in model.forwardSearch(place).mapToSet { set -> set.mapToSet { it.name } }.sortedBy { it.size }) {
                if (following.none { subset -> set.containsAll(subset) })
                    following.add(set)
            }
            return@computeIfAbsent following
        }
    }

    override fun reset() {
        consumentsCache.clear()
    }

    override fun compute(startIndex: Int, nEvents: List<Map<String?, Int>>, prevProcessState: ProcessModelState): Int {
        prevProcessState as Marking
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
        for ((place, counter) in prevProcessState) {
            val following = this.following(place)
            val consuments = consumentsCache.computeIfAbsent(place to startIndex) {
                following.sumOf { set -> set.minOf { nEvents[it] ?: 0 } }
            }
            if (counter.size > consuments)
                nonConsumable.add(following to counter.size - consuments)
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
