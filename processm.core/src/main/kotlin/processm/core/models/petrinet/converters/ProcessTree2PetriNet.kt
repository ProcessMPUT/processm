package processm.core.models.petrinet.converters

import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import processm.core.models.processtree.*
import java.util.*

class ProcessTree2PetriNet(val tree: ProcessTree) {

    private val activities = IdentityHashMap<ProcessTreeActivity, UUID>()
    private val uuidToActivity = IdentityHashMap<UUID, ProcessTreeActivity>()
    private val start = Place()
    private val end = Place()
    private val inPlaces = HashMap<UUID, HashSet<Place>>()
    private val outPlaces = HashMap<UUID, HashSet<Place>>()
    private val auxiliaries = HashSet<UUID>()

    private fun convert(tnode: Node, before: Place, after: Place): Unit = when (tnode) {
        is Sequence -> convertSequence(tnode, before, after)
        is ProcessTreeActivity -> convertActivity(tnode, before, after)
        is Exclusive -> convertExclusive(tnode, before, after)
        is Parallel -> convertParallel(tnode, before, after)
        is RedoLoop -> convertRedoLoop(tnode, before, after)
        else -> throw IllegalArgumentException("Unknown node type: ${tnode.javaClass.name}")
    }

    private fun convertRedoLoop(tnode: RedoLoop, before: Place, after: Place) {
        val pBeforeLoop = Place()
        val pInLoop = Place()
        val tb = UUID.randomUUID()
        val ta = UUID.randomUUID()
        auxiliaries.add(tb)
        auxiliaries.add(ta)
        inPlaces[tb] = hashSetOf(before)
        outPlaces[tb] = hashSetOf(pBeforeLoop)
        inPlaces[ta] = hashSetOf(pInLoop)
        outPlaces[ta] = hashSetOf(after)
        convert(tnode.children.first(), pBeforeLoop, pInLoop)
        for (i in 1 until tnode.children.size)
            convert(tnode.children[i], pInLoop, pBeforeLoop)
    }

    private fun convertParallel(tnode: Parallel, before: Place, after: Place) {
        val tb = UUID.randomUUID()
        val ta = UUID.randomUUID()
        auxiliaries.add(tb)
        auxiliaries.add(ta)
        inPlaces[tb] = hashSetOf(before)
        outPlaces[ta] = hashSetOf(after)
        tnode.children.forEach {
            val b = Place()
            val a = Place()
            outPlaces.computeIfAbsent(tb) { HashSet() }.add(b)
            inPlaces.computeIfAbsent(ta) { HashSet() }.add(a)
            convert(it, b, a)
        }
    }

    private fun convertExclusive(tnode: Exclusive, before: Place, after: Place) {
        tnode.children.forEach { convert(it, before, after) }
    }

    private fun convertActivity(tnode: ProcessTreeActivity, before: Place, after: Place) {
        val transition = activities.computeIfAbsent(tnode) {
            val id = UUID.randomUUID()
            uuidToActivity[id] = it
            return@computeIfAbsent id
        }
        inPlaces.computeIfAbsent(transition) { HashSet() }.add(before)
        outPlaces.computeIfAbsent(transition) { HashSet() }.add(after)
    }

    private fun convertSequence(tnode: Sequence, before: Place, after: Place) {
        var b = before
        val i = tnode.children.iterator()
        while (i.hasNext()) {
            val item = i.next()
            val e = if (i.hasNext()) Place() else after
            convert(item, b, e)
            b = e
        }
    }

    private fun removeRedundantSilents(): Boolean {
        val candidates = auxiliaries + activities.filter { it.key.isSilent }.values
        var modified = false
        for (c in candidates) {
            val d = auxiliaries.singleOrNull { outPlaces[c] == inPlaces[it] } ?: continue
            outPlaces.remove(d)?.let {
                outPlaces.merge(c, it) { a, b ->
                    a.addAll(b)
                    a
                }
            }
            inPlaces.remove(d)?.let {
                inPlaces.merge(c, it) { a, b ->
                    a.addAll(b)
                    a
                }
            }
            removeTransition(d)
            modified = true
        }
        return modified
    }

    private fun isSilent(id: UUID) = id in auxiliaries || (uuidToActivity[id]?.isSilent == true)

    private fun removeDanglingPlaces(): Boolean {
        val precedingTransitions = computePrecedingTransitions()
        val succeedingTransitions = computeSucceedingTransitions()
        var modified = false
        for ((place, transitions) in precedingTransitions) {
            val transitionId = transitions.singleOrNull() ?: continue
            if (!isSilent(transitionId)) continue
            if (succeedingTransitions[place]?.singleOrNull() == transitionId) {
                inPlaces[transitionId]?.remove(place)
                outPlaces[transitionId]?.remove(place)
                modified = true
            }
        }
        return modified
    }

    private fun computePrecedingTransitions() = HashMap<Place, HashSet<UUID>>().apply {
        for ((transition, places) in outPlaces)
            for (place in places)
                computeIfAbsent(place) { HashSet() }.add(transition)
    }

    private fun computeSucceedingTransitions() = HashMap<Place, HashSet<UUID>>().apply {
        for ((transition, places) in inPlaces)
            for (place in places)
                computeIfAbsent(place) { HashSet() }.add(transition)
    }

    private fun removeTransition(transitionId: UUID) {
        auxiliaries.remove(transitionId)
        uuidToActivity[transitionId]?.let {
            activities.remove(it)
            uuidToActivity.remove(transitionId)
        }
        outPlaces.remove(transitionId)
        inPlaces.remove(transitionId)
    }

    private fun simplifySelfLoops(): Boolean {
        // before: 1 external input, 1 input from the silent transaction, 1 output to the named transaction
        // after: 1 input from the named transaction, 1 external output, 1 output to the silent transaction
        // the silent transaction
        // the named transaction
        var modified = false
        val allNonSilent = ArrayList<UUID>()
        val allSilent = HashSet(auxiliaries)
        for ((key, value) in activities)
            if (key.isSilent)
                allSilent.add(value)
            else
                allNonSilent.add(value)
        val precedingTransitions = computePrecedingTransitions()
        val succeedingTransitions = computeSucceedingTransitions()
        for (named in allNonSilent) {
            val before = inPlaces[named]?.singleOrNull() ?: continue
            if (succeedingTransitions[before]?.singleOrNull() != named) continue
            val after = outPlaces[named]?.singleOrNull() ?: continue
            if (precedingTransitions[after]?.singleOrNull() != named) continue
            val silent =
                succeedingTransitions[after].orEmpty().intersect(precedingTransitions[before].orEmpty()).singleOrNull()
                    ?: continue
            if (silent !in allSilent) continue
            // silent is removed, after is now going to named directly
            removeTransition(silent)
            inPlaces[named]!!.add(after)
            modified = true
        }
        return modified
    }

    private fun trimStart(): Boolean {
        val succeeding = computeSucceedingTransitions()
        val preceding = computePrecedingTransitions()
        var modified = false
        for (place in succeeding.keys - preceding.keys) {
            val transitionId = succeeding[place]?.singleOrNull() ?: continue
            if (!isSilent(transitionId)) continue
            val succeedingPlace = outPlaces[transitionId]?.singleOrNull() ?: continue
            if (preceding[succeedingPlace] == setOf(transitionId)) {
                removeTransition(transitionId)
                modified = true
            }
        }
        return modified
    }

    private fun trimEnd(): Boolean {
        val succeeding = computeSucceedingTransitions()
        val preceding = computePrecedingTransitions()
        var modified = false
        for (place in preceding.keys - succeeding.keys) {
            val transitionId = preceding[place]?.singleOrNull() ?: continue
            if (!isSilent(transitionId)) continue
            val precedingPlace = inPlaces[transitionId]?.singleOrNull() ?: continue
            if (succeeding[precedingPlace] == setOf(transitionId)) {
                removeTransition(transitionId)
                modified = true
            }
        }
        return modified
    }

    fun toPetriNet(): PetriNet {
        tree.root?.let { convert(it, start, end) }
        var modified = true
        while (modified) {
            modified =
                removeRedundantSilents() or simplifySelfLoops() or removeDanglingPlaces() or trimStart() or trimEnd()
        }
        val transitions = activities.mapTo(ArrayList()) { (activitiy, id) ->
            Transition(
                name = activitiy.name,
                inPlaces = inPlaces[id].orEmpty(),
                outPlaces = outPlaces[id].orEmpty(),
                isSilent = activitiy.isSilent,
                id = id
            )
        }
        auxiliaries.mapTo(transitions) { id ->
            Transition(
                "τ",
                inPlaces = inPlaces[id].orEmpty(),
                outPlaces = outPlaces[id].orEmpty(),
                isSilent = true,
                id = id
            )
        }
        val places = HashSet<Place>()
        val preceding = computePrecedingTransitions()
        val succeeding = computeSucceedingTransitions()
        val start = (succeeding.keys - preceding.keys).associateWith { 1 }
        val end = (preceding.keys - succeeding.keys).associateWith { 1 }
        inPlaces.values.forEach(places::addAll)
        outPlaces.values.forEach(places::addAll)
        return PetriNet(places.toList(), transitions, initialMarking = Marking(start), finalMarking = Marking(end))
    }
}

fun ProcessTree.toPetriNet(): PetriNet = ProcessTree2PetriNet(this).toPetriNet()
