package processm.core.models.petrinet.converters

import org.apache.commons.collections4.BidiMap
import org.apache.commons.collections4.bidimap.DualHashBidiMap
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import processm.helpers.map2d.DoublingMap2D
import processm.helpers.optimize

/**
 * A converter for [CausalNet] into [PetriNet].
 *
 * If there are multiple instances of the same activity (i.e., sharing the same name and differing only in [Node.instanceId])
 * in the [CausalNet], they are represented in the [PetriNet] such that there is a single, non-silent [Transition] shared
 * by all these activities and named according to their shared name, registered in [mainTransitions] and two silent [Transition]s
 * for each instance, one executed before the main transition and registered in [node2InboundSilentTransition], and the other
 * after the main transition, registered in [node2OutboundSilentTransition]. Their purpose is to keep track which
 * particular instance is executed without intruding into names of non-silent transitions (e.g., by creating a separate
 * [Transition] for each instance and suffixing their names with instance IDs)
 */
class CausalNet2PetriNet(val cnet: CausalNet) {
    private val places = ArrayList<Place>()

    private val startPlace: Place = addPlace()
    private val endPlace: Place = addPlace()
    private val placesOnArcs = DoublingMap2D<Node, Node, Place>()

    /**
     * A bidirectional map from splits of [cnet] to the corresponding [Transition]s of the resulting PetriNet
     *
     * If, for a given activity, there exists exactly one split, and it consists of a single dependency, then
     * the corresponding transition is not created, and [split2SilentTransition] will not contain such a split
     */
    val split2SilentTransition: BidiMap<Split, Transition>
        get() = split2SilentTransitionInternal
    private val split2SilentTransitionInternal = DualHashBidiMap<Split, Transition>()

    /**
     * A bidirectional map from joins of [cnet] to the corresponding [Transition]s of the resulting PetriNet
     *
     * If, for a given activity, there exists exactly one join, and it consists of a single dependency, then
     * the corresponding transition is not created, and [join2SilentTransition] will not contain such a split
     */
    val join2SilentTransition: BidiMap<Join, Transition>
        get() = join2SilentTransitionInternal
    private val join2SilentTransitionInternal = DualHashBidiMap<Join, Transition>()

    /**
     * A bidirectional map from nodes of [cnet] to the [Transition]s corresponding to them.
     * Contains only nodes not present in [mainTransitions]
     *
     * Every node of [cnet] should be present there
     */
    val node2Transition: BidiMap<Node, Transition>
        get() = node2TransitionInternal
    private val node2TransitionInternal = DualHashBidiMap<Node, Transition>()


    /**
     * Mapping from node name to [Transition], valid only for nodes with multiple instances.
     * See class description for more details.
     */
    val mainTransitions: Map<String, Transition>
        get() = mainTransitionsInternal
    private val mainTransitionsInternal = HashMap<String, Transition>()

    /**
     * Bidirectional mapping from node to silent [Transition], valid only for nodes with multiple instances.
     * See class description for more details.
     */
    val node2InboundSilentTransition: BidiMap<Node, Transition>
        get() = node2InboundSilentTransitionInternal
    private val node2InboundSilentTransitionInternal = DualHashBidiMap<Node, Transition>()

    /**
     * Bidirectional mapping from node to silent [Transition], valid only for nodes with multiple instances.
     * See class description for more details.
     */
    val node2OutboundSilentTransition: BidiMap<Node, Transition>
        get() = node2OutboundSilentTransitionInternal
    private val node2OutboundSilentTransitionInternal = DualHashBidiMap<Node, Transition>()

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
            join2SilentTransitionInternal[join] = silentTransition
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
            split2SilentTransitionInternal[split] = silentTransition
        }
        return afterNodePlace
    }

    /**
     * True if there are at least 2 non-silent nodes sharing the same name (key), false otherwise
     */
    private val isMultiInstance: Map<String, Boolean> =
        cnet.instances.groupBy { it.name }.mapValues { it.value.count { !it.isSilent } > 1 }

    private fun mapNode(node: Node) {
        val beforeNodePlace = mapJoins(node)
        val afterNodePlace = mapSplits(node)
        if (isMultiInstance[node.name] != true) {
            val transition = Transition(
                name = node.name,
                inPlaces = beforeNodePlace,
                outPlaces = afterNodePlace,
                isSilent = node.isSilent
            )
            assert(node !in node2TransitionInternal)
            node2TransitionInternal[node] = transition
        } else {
            val mainTransition = mainTransitionsInternal.computeIfAbsent(node.name) {
                Transition(
                    name = node.name,
                    inPlaces = listOf(Place()),
                    outPlaces = listOf(Place()),
                    isSilent = node.isSilent
                )
            }
            val auxPlace = Place()
            assert(node !in node2InboundSilentTransitionInternal)
            node2InboundSilentTransitionInternal[node] = Transition(
                name = "in ${node.name}/${node.instanceId}",
                inPlaces = beforeNodePlace,
                outPlaces = mainTransition.inPlaces + listOf(auxPlace),
                isSilent = true
            )
            assert(node !in node2OutboundSilentTransitionInternal)
            node2OutboundSilentTransitionInternal[node] = Transition(
                name = "out ${node.name}/${node.instanceId}",
                inPlaces = mainTransition.outPlaces + listOf(auxPlace),
                outPlaces = afterNodePlace,
                isSilent = true
            )
        }
    }

    init {
        createPlacesOnArcs()
        cnet.instances.forEach(::mapNode)
        assert(cnet.instances.all { node -> node in node2OutboundSilentTransition || node in node2InboundSilentTransition || node in node2Transition })
    }

    fun toPetriNet(): PetriNet = PetriNet(
        places = places,
        transitions = (split2SilentTransitionInternal.values + join2SilentTransitionInternal.values + node2TransitionInternal.values + mainTransitionsInternal.values + node2OutboundSilentTransitionInternal.values + node2InboundSilentTransitionInternal.values).toList(),
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
