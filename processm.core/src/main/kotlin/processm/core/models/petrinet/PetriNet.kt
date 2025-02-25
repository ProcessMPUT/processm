package processm.core.models.petrinet

import com.carrotsearch.hppc.ObjectIdentityHashSet
import processm.core.models.commons.*
import processm.core.models.commons.DecisionPoint
import processm.core.models.metadata.DefaultMutableMetadataHandler
import processm.core.models.metadata.MetadataHandler
import processm.helpers.cartesianProduct
import processm.helpers.optimize
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents the Petri net.
 * @property places the collection of places.
 * @property transitions the collection of transitions (activities).
 * @property initialMarking the initial marking, from which the process starts.
 * @property finalMarking the final marking, at which the process ends.
 */
class PetriNet(
    val places: List<Place>,
    val transitions: List<Transition>,
    val initialMarking: Marking = places.firstOrNull()?.let { Marking(it) } ?: Marking.empty,
    val finalMarking: Marking = places.lastOrNull()?.let { Marking(it) } ?: Marking.empty,
    private val metadataHandler: MetadataHandler = DefaultMutableMetadataHandler()
) : ProcessModel, MetadataHandler by metadataHandler {
    override val activities: List<Activity> = transitions

    override val decisionPoints: Sequence<DecisionPoint> = sequence {
        // FIXME: This sequence works in O(|T|^2*|P|^2). It can be reduced to O(|T|*|P|) using a mapping from places to transitions.
        val forbidden = ArrayList<Set<Transition>>()

        var res: HashSet<Transition>? = null

        for ((i, t1) in transitions.withIndex()) {
            if (t1.inPlaces.isEmpty())
                continue

            if (res === null)
                res = HashSet()
            assert(res.isEmpty())
            res.add(t1)

            for (t2 in transitions.listIterator(i + 1)) {
                if (forbidden.any { f -> t1 in f && t2 in f })
                    continue
                if (t1.inPlaces.any { p1 -> p1 in t2.inPlaces })
                    res.add(t2)
            }

            if (res.size > 1) {
                val commonPlaces = res.flatMapTo(HashSet()) { t -> t.inPlaces }
                val previousActivities =
                    transitions.filterTo(HashSet()) { t -> t.outPlaces.any { commonPlaces.contains(it) } }
                yield(DecisionPoint(commonPlaces, res, previousActivities))
                forbidden.add(res)
                res = null
            } else {
                res.clear()
            }
        }
    }

    override val controlStructures: Sequence<ControlStructure>
        get() = sequence {
            for ((place, transitions) in placeToFollowingTransition) {
                if (place === null)
                    continue
                if (transitions.size > 1)
                    yield(XOR(ControlStructureType.XorSplit, place, transitions))
                else if (transitions.size == 1) {
                    val prevTransitions = placeToPrecedingTransition[place].orEmpty()
                    if (prevTransitions.size == 1)
                        yield(Causality(prevTransitions[0], transitions[0]))
                }
            }

            for ((place, transitions) in placeToPrecedingTransition) {
                if (place === null)
                    continue
                if (transitions.size > 1)
                    yield(XOR(ControlStructureType.XorJoin, place, transitions))
            }

            for (transition in transitions) {
                if (transition.outPlaces.size > 1)
                    yield(AND(ControlStructureType.AndSplit, transition.outPlaces, transition))
                if (transition.inPlaces.size > 1)
                    yield(AND(ControlStructureType.AndJoin, transition.inPlaces, transition))
            }
        }

    override fun createInstance(): PetriNetInstance = PetriNetInstance(this)

    /**
     * A mapping from a place to the list of following transitions.
     */
    val placeToFollowingTransition: Map<Place?, List<Transition>> =
        HashMap<Place?, List<Transition>>(places.size * 4 / 3, 0.75f).apply {
            for (transition in transitions) {
                if (transition.inPlaces.isEmpty()) {
                    (computeIfAbsent(null) { ArrayList() } as ArrayList<Transition>).add(transition)
                } else {
                    for (place in transition.inPlaces) {
                        (computeIfAbsent(place) { ArrayList() } as ArrayList<Transition>).add(transition)
                    }
                }
            }
            for (entry in this)
                entry.setValue(entry.value.optimize())
        }

    /**
     * A mapping from a place to the list of preceding transitions.
     */
    val placeToPrecedingTransition: Map<Place?, List<Transition>> =
        HashMap<Place?, List<Transition>>(places.size * 4 / 3, 0.75f).apply {
            for (transition in transitions) {
                if (transition.outPlaces.isEmpty()) {
                    (computeIfAbsent(null) { ArrayList() } as ArrayList<Transition>).add(transition)
                } else {
                    for (place in transition.outPlaces) {
                        (computeIfAbsent(place) { ArrayList() } as ArrayList<Transition>).add(transition)
                    }
                }
            }
            for (entry in this)
                entry.setValue(entry.value.optimize())
        }

    override val startActivities: List<Activity> = available(initialMarking).toList()

    override val endActivities: List<Activity> = backwardAvailable(finalMarking).toList()

    /**
     * Calculates the collection of transitions enabled for firing at given [marking].
     */
    fun available(marking: Marking): Sequence<Transition> = sequence {
        if (marking.isNotEmpty()) {
            val visited = ObjectIdentityHashSet<Transition>()
            for (place in marking.keys) {
                val transitions = placeToFollowingTransition[place] ?: continue
                var index = 0
                while (index < transitions.size) {
                    val transition = transitions[index++]
                    if (!visited.add(transition))
                        continue

                    if (transition.inPlaces.size <= 1 || isAvailable(transition, marking))
                        yield(transition)
                }
            }
        }

        yieldAll(placeToFollowingTransition[null].orEmpty())
    }

    /**
     * Verifies whether [transition] is enabled in the given [marking].
     */
    fun isAvailable(transition: Transition, marking: Marking): Boolean {
        val inPlaces = transition.inPlaces
        return inPlaces.size <= marking.size && inPlaces.all(marking::containsKey)
    }

    /**
     * Calculates the collection of transitions, whose execution might yield the given [marking].
     * The returned transitions might not actually run.
     */
    fun backwardAvailable(marking: Marking): Sequence<Transition> = sequence {
        if (marking.isNotEmpty()) {
            val markingKeys = marking.keys
            val markingSize = markingKeys.size
            val visited = IdentityHashMap<Transition, Unit>(transitions.size * 4 / 3)
            for (place in markingKeys) {
                for (transition in placeToPrecedingTransition[place].orEmpty()) {
                    if (visited.put(transition, Unit) !== null)
                        continue

                    if (transition.outPlaces.size <= 1 || isBackwardAvailable(transition, marking))
                        yield(transition)
                }
            }
        }

        yieldAll(placeToPrecedingTransition[null].orEmpty())
    }

    /**
     * Verifies whether the given [transition] is enabled in [marking] if all arcs are reversed.
     */
    fun isBackwardAvailable(transition: Transition, marking: Marking): Boolean {
        val outPlaces = transition.outPlaces
        return outPlaces.size <= marking.size && outPlaces.all(marking::containsKey)
    }

    /**
     * Produces a copy of this [PetriNet] stripped out of transitions that must not run and places without succeeding
     * transitions except the places in the [finalMarking].
     */
    fun dropDeadParts(): PetriNet {
        // drop transitions that must not run because there is no way to put token in its input place(s)
        val usedTransitions = transitions.filter { t ->
            t.inPlaces.all { p -> p in initialMarking || transitions.any { t2 -> p in t2.outPlaces } }
        }

        // drop places that do not have outgoing arc to a transition except the places in the end marking
        val usedPlaces = places.filter { p ->
            p in initialMarking || p in finalMarking || usedTransitions.any { t -> p in t.inPlaces }
        }

        // drop unused out places from transitions
        val outTransitions = usedTransitions.map { t ->
            t.copy(outPlaces = t.outPlaces.filter { p -> p in usedPlaces })
        }

        assert(initialMarking.keys.all { p -> p in usedPlaces })
        assert(finalMarking.keys.all { p -> p in usedPlaces })
        assert(outTransitions.all { t -> usedPlaces.containsAll(t.inPlaces) })
        assert(outTransitions.all { t -> usedPlaces.containsAll(t.outPlaces) })

        return PetriNet(usedPlaces, outTransitions, initialMarking, finalMarking)
    }

    private val hashCode: Int by lazy {
        Objects.hash(*transitions.map { Triple(it.name, it.inPlaces.size, it.outPlaces.size) }.sortedWith { a, b ->
            val x = a.first.compareTo(b.first)
            if (x != 0)
                return@sortedWith x
            val y = a.second.compareTo(b.second)
            if (y != 0)
                return@sortedWith y
            return@sortedWith a.third.compareTo(b.third)

        }.toTypedArray())
    }

    override fun hashCode(): Int = hashCode

    override fun equals(other: Any?): Boolean =
        (this === other) || (other is PetriNet && this.hashCode == other.hashCode && equalsInternal(other))

    private fun generateTransitionsMap(
        mine2theirs: List<List<Int>>,
        startAt: Int = 0,
        theirsUsed: Set<Int> = emptySet()
    ): Sequence<List<Int>> = sequence {
        var pos = startAt
        val prefix = ArrayList<Int>()
        while (pos < mine2theirs.size && mine2theirs[pos].size == 1) {
            val theirs = mine2theirs[pos].single()
            if (theirs in theirsUsed || theirs in prefix)
                return@sequence
            prefix.add(theirs)
            pos++
        }
        assert(prefix.size == pos - startAt)
        val newTheirsUsed = theirsUsed + prefix
        if (pos < mine2theirs.size) {
            for (theirs in mine2theirs[pos])
                if (theirs !in newTheirsUsed)
                    for (tail in generateTransitionsMap(mine2theirs, pos + 1, newTheirsUsed + setOf(theirs)))
                        yield(prefix + setOf(theirs) + tail)
        } else
            yield(prefix)
    }

    // TODO verify the assumption that no transition occurs more than once in any of the lists
    private fun compareTransitionLists(left: List<Transition>?, right: List<Transition>?) =
        left?.toSet().orEmpty() == right?.toSet().orEmpty()

    private fun matchPlaces(
        minePlaces: Collection<Place>,
        theirsPlaces: Collection<Place>,
        transitionsMap: Map<Transition, Transition>,
        other: PetriNet
    ): Boolean {
        assert(minePlaces.size == theirsPlaces.size)
        val theirsAvailable = theirsPlaces.toMutableSet()
        for (mine in minePlaces) {
            val mineInTransitions = placeToPrecedingTransition[mine]?.mapNotNull { transitionsMap[it] }
            val mineOutTransitions = placeToFollowingTransition[mine]?.mapNotNull { transitionsMap[it] }
            var hit = false
            for (theirs in theirsAvailable) {
                if (compareTransitionLists(mineInTransitions, other.placeToPrecedingTransition[theirs]) &&
                    compareTransitionLists(mineOutTransitions, other.placeToFollowingTransition[theirs])
                ) {
                    hit = true
                    theirsAvailable.remove(theirs)
                    break
                }
            }
            if (!hit)
                return false
        }
        return true
    }

    private fun equalsInternal(other: PetriNet): Boolean {
        if (this.transitions.size != other.transitions.size)
            return false
        val mine2theirs = List(this.transitions.size) { ArrayList<Int>() }
        for (mine in this.transitions.indices)
            for (theirs in other.transitions.indices) {
                val mineT = this.transitions[mine]
                val theirsT = other.transitions[theirs]
                if (mineT.name == theirsT.name && mineT.inPlaces.size == theirsT.inPlaces.size && mineT.outPlaces.size == theirsT.outPlaces.size) {
                    mine2theirs[mine].add(theirs)
                }
            }
        for (transitionsMap in generateTransitionsMap(mine2theirs)) {
            assert(transitionsMap.size == this.transitions.size)
            val transitionMap2 = (this.transitions.indices zip transitionsMap)
                .map { (mine, theirs) -> this.transitions[mine] to other.transitions[theirs] }
                .toMap()
            var hit = true
            for ((mine, theirs) in transitionMap2.entries) {
                if (!(matchPlaces(mine.inPlaces, theirs.inPlaces, transitionMap2, other) &&
                            matchPlaces(mine.outPlaces, theirs.outPlaces, transitionMap2, other))
                ) {
                    hit = false
                    break
                }
            }
            if (hit)
                return true
        }
        return false
    }

    fun toMultilineString(): String {
        val result = StringBuilder()
        val place2id = HashMap<Place, String>()
        fun placeToString(p: Place) = place2id.computeIfAbsent(p) { "p${place2id.size}" }
        for (t in transitions) {
            result.append(
                t.inPlaces.joinToString(
                    separator = " ",
                    prefix = "[",
                    postfix = "]",
                    transform = ::placeToString
                )
            )
            result.append(" -> ")
            result.append(t.name)
            result.append(" -> ")
            result.append(
                t.outPlaces.joinToString(
                    separator = " ",
                    prefix = "[",
                    postfix = "]",
                    transform = ::placeToString
                )
            )
            result.appendLine()
        }
        return result.toString()
    }

    private fun forwardSearchInternal(start: Place, visited: Set<Transition>): List<Set<Transition>> {
        val result = ArrayList<Set<Transition>>()
        for (t in placeToFollowingTransition[start].orEmpty()) {
            if (t.isSilent) {
                if (t !in visited) {
                    val visitedPlusT = visited + setOf(t)
                    t.outPlaces.map { forwardSearchInternal(it, visitedPlusT) as ArrayList }.cartesianProduct()
                        .mapTo(result) { parts -> parts.flatMapTo(HashSet()) { it } }
                }
            } else
                result.add(setOf(t))
        }
        return result
    }

    private val forwardSearchCache = ConcurrentHashMap<Place, List<Set<Transition>>>()

    /**
     * Returns sets of transitions that can be executed for a single token in [start].
     * Differs from [placeToFollowingTransition] in that it goes over silent transitions, so a single token may actually
     * lead to execution of multiple (non-silent) transitions. This is an overestimation, i.e., it assumes that
     * all other tokens are available/treats AND joins inbound for a transition as OR joins.
     *
     * The result is to be treated as a XOR of ANDs, i.e., transitions in a single set must be executed jointly, while
     * from the top-level sequence exactly one set must be executed.
     */
    fun forwardSearch(start: Place): List<Set<Transition>> = forwardSearchCache.computeIfAbsent(start) {
        forwardSearchInternal(it, emptySet())
    }

    /**
     * Calculates the collection of transitions that are the direct cause of firing the given [transition] in
     * the given [marking]. The results of this method rely on the [Token.producer] property indicating the transition
     * that produced the token. For [marking] consisting of places with multiple tokens, the FIFO order of
     * production/consumption is assumed.
     * @param transition Transition to find cause for.
     * @param marking The marking before running [transition].
     * @return The collection of transitions immediately preceding the input places of the given [transition]. The empty
     * collection for the start transition.
     */
    fun getCause(transition: Transition, marking: Marking): Array<Transition> {
        check(isAvailable(transition, marking)) { "Transition $transition is not available in marking $marking." }

        return transition.inPlaces.mapNotNullTo(HashSet()) { marking[it]!!.first().producer }.toTypedArray()
    }

    /**
     * Calculates the collection of dependent transitions that are the direct consequence of firing the given [transition]
     * resulting in the given [marking]. The results of this method rely on the [Token.producer] property indicating the
     * transition that produced the token. For [marking] consisting of places with multiple tokens, the LIFO order of
     * production/consumption is assumed.
     * @param transition Transition to find consequence for.
     * @param marking The marking after running [transition].
     * @return The collection of transitions immediately following the output places of the given [transition]. The empty
     * collection for the end transition.
     */
    fun getConsequence(transition: Transition, marking: Marking): Array<Transition> {
        check(isBackwardAvailable(transition, marking)) {
            "Transition $transition is not backward available in marking $marking."
        }

        return transition.outPlaces.mapNotNullTo(HashSet()) { marking[it]!!.last().producer }.toTypedArray()
    }
}
