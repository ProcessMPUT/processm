package processm.core.models.petrinet.converters

import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.optimize
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition

/**
 * A converter for [CausalNet] into [PetriNet].
 */
private class CausalNet2PetriNet(val cnet: CausalNet) {
    private val places = ArrayList<Place>()
    private val transitions = ArrayList<Transition>()
    private val startPlace: Place = addPlace()
    private val endPlace: Place = addPlace()
    private val placesOnArcs = DoublingMap2D<Node, Node, Place>()

    private fun addPlace(): Place {
        val place = Place()
        places.add(place)
        return place
    }

    private fun createPlacesOnArcs() {
        for (depencency in cnet.dependencies)
            placesOnArcs[depencency.source, depencency.target] = addPlace()
    }

    private fun mapJoins(node: Node): List<Place> {
        val joins = cnet.joins[node] ?: let { assert(cnet.start === node); return@mapJoins listOf(startPlace) }

        assert(joins.isNotEmpty())

        if (joins.size == 1 && joins.first().sources.size == 1) {
            // skip unary join
            return listOf(placesOnArcs[joins.first().sources.first(), node]!!)
        }

        val beforeNodePlace = listOf(addPlace())
        for (join in joins) {
            assert(join.sources.isNotEmpty())
            val silentTransition = Transition(
                name = "τ",
                inPlaces = join.sources.map { placesOnArcs[it, node]!! }.optimize(),
                outPlaces = beforeNodePlace,
                isSilent = true
            )
            transitions.add(silentTransition)
        }
        return beforeNodePlace
    }

    private fun mapSplits(node: Node): List<Place> {
        val splits = cnet.splits[node] ?: let { assert(cnet.end === node); return listOf(endPlace) }

        assert(splits.isNotEmpty())

        if (splits.size == 1 && splits.first().targets.size == 1) {
            // skip unary split
            return listOf(placesOnArcs[node, splits.first().targets.first()]!!)
        }

        val afterNodePlace = listOf(addPlace())
        for (split in splits) {
            assert(split.targets.isNotEmpty())
            val silentTransition = Transition(
                name = "τ",
                inPlaces = afterNodePlace,
                outPlaces = split.targets.map { placesOnArcs[node, it]!! }.optimize(),
                isSilent = true
            )
            transitions.add(silentTransition)
        }
        return afterNodePlace
    }

    private fun mapNode(node: Node) {
        val beforeNodePlace = mapJoins(node)
        val afterNodePlace = mapSplits(node)
        val transition = Transition(
            name = node.name,
            inPlaces = beforeNodePlace,
            outPlaces = afterNodePlace,
            isSilent = node.isSilent
        )
        transitions.add(transition)
    }

    init {
        createPlacesOnArcs()
        cnet.instances.forEach(::mapNode)
    }

    fun toPetriNet(): PetriNet = PetriNet(
        places = places,
        transitions = transitions,
        initialMarking = Marking(startPlace),
        finalMarking = Marking(endPlace)
    )
}

/**
 * Converts this [CausalNet] into a [PetriNet].
 * The semantics of the process model may change due to this conversion, since the validity of the move in the Causal
 * net is defined globally using valid binding sequences and the validity of the move in the Petri net is defined
 * locally based on the current marking. In other words, the entire history and the entire future of moves influence the
 * validity of the move in Causal net, while only the current marking influences the validity of the move in Petri net.
 * Consequently, every valid binding sequence in this [CausalNet] corresponds to a valid firing sequence in the
 * resulting [PetriNet], but the resulting [PetriNet] may allow for a valid firing sequence that does not correspond
 * to a valid binding sequence in this [CausalNet].
 */
fun CausalNet.toPetriNet(): PetriNet = CausalNet2PetriNet(this).toPetriNet().dropDeadParts()
