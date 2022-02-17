package processm.conformance.models.alignments

import processm.conformance.models.alignments.events.EventsSummarizer
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.core.models.commons.ProcessModel
import processm.core.models.petrinet.Transition
import processm.core.models.petrinet.converters.CausalNet2PetriNet
import java.util.concurrent.ExecutorService

class CausalNetAsPetriNetAligner(private val base: Aligner, private val converter: CausalNet2PetriNet) :
    Aligner by base {

    companion object {
        private val logger = logger()
    }

    private val silentTransition2Split: Map<Transition, Split>
        get() = converter.split2SilentTransition.inverseBidiMap()
    private val silentTransition2Join: Map<Transition, Join>
        get() = converter.join2SilentTransition.inverseBidiMap()
    private val inboundSilentTransition2Node: Map<Transition, Node>
        get() = converter.node2InboundSilentTransition.inverseBidiMap()
    private val outboundSilentTransition2Node: Map<Transition, Node>
        get() = converter.node2OutboundSilentTransition.inverseBidiMap()

    private val transition2Node: Map<Transition, Node>
        get() = converter.node2Transition.inverseBidiMap()

    override fun align(trace: Trace): Alignment = translate(base.align(trace))

    override fun align(log: Log, summarizer: EventsSummarizer<*>?): Sequence<Alignment> =
        base.align(log, summarizer).map(this::translate)

    override fun align(log: Sequence<Trace>, summarizer: EventsSummarizer<*>?): Sequence<Alignment> =
        base.align(log, summarizer).map(this::translate)

    private fun <T : Comparable<T>> Iterable<T>.isNonDescending(): Boolean {
        val i = iterator()
        var prev: T = i.next()
        while (i.hasNext()) {
            val curr = i.next()
            if (curr < prev)
                return false
            prev = curr
        }
        return true
    }

    private fun translate(petriNetAlignment: Alignment): Alignment {
        if (logger.isDebugEnabled) {
            for ((idx, petriNetStep) in petriNetAlignment.steps.withIndex()) {
                val modelMove = petriNetStep.modelMove
                val modelMoveAsSplit = silentTransition2Split[modelMove]
                val modelMoveAsJoin = silentTransition2Join[modelMove]
                val modelMoveAsNode = transition2Node[modelMove]
                val modelMoveAsInbound = inboundSilentTransition2Node[modelMove]
                val modelMoveAsOutbound = outboundSilentTransition2Node[modelMove]
                logger.debug { "$idx $modelMove $modelMoveAsJoin $modelMoveAsNode $modelMoveAsSplit $modelMoveAsInbound $modelMoveAsOutbound" }
                assert(modelMove == null || modelMove in base.model.activities)
            }
        }
        val causalNetSteps = ArrayList<Step>()
        val instance = converter.cnet.createInstance()

        val ranges = ArrayList<IntRange?>()
        val splits = HashMap<Node, ArrayList<Int>>()
        val joins = HashMap<Node, ArrayList<Int>>()

        run {
            val last = HashMap<Node, Int>()
            for ((idx, step) in petriNetAlignment.steps.withIndex()) {
                val node = transition2Node[step.modelMove]
                if (node !== null) {
                    val lastIdx = last[node]
                    if (lastIdx !== null)
                        ranges[lastIdx] = ranges[lastIdx]!!.first..idx
                    ranges.add((lastIdx ?: 0)..petriNetAlignment.steps.size)
                    last[node] = idx
                } else {
                    ranges.add(null)
                    silentTransition2Split[step.modelMove]?.let { split ->
                        splits.computeIfAbsent(split.source) { ArrayList() }.add(idx)
                    }
                    silentTransition2Join[step.modelMove]?.let { join ->
                        joins.computeIfAbsent(join.target) { ArrayList() }.add(idx)
                    }
                }
            }
        }

        val nextInstance = HashMap<String, ArrayDeque<Node>>()
        for ((idx, step) in petriNetAlignment.steps.withIndex()) {
            logger.debug { "$idx $step" }
            if (step.modelMove !== null) {
                val node = transition2Node[step.modelMove] ?: nextInstance[step.modelMove.name]?.removeFirstOrNull()
                if (node !== null) {
                    logger.debug { "node = $node range = ${ranges[idx]} joins = ${joins[node]} splits = ${splits[node]}" }

                    assert(joins[node]?.isNonDescending() != false)
                    val join = ranges[idx]?.let { range ->
                        joins[node]?.firstOrNull { range.first <= it && it < idx }?.let { joinIdx ->
                            joins[node]?.remove(joinIdx)
                            silentTransition2Join[petriNetAlignment.steps[joinIdx].modelMove]
                        }
                    } ?: converter.cnet.joins[node]?.single()
                    assert((join === null) == (converter.cnet.joins[node].isNullOrEmpty()))

                    assert(splits[node]?.isNonDescending() != false)
                    val split = ranges[idx]?.let { range ->
                        splits[node]?.firstOrNull { idx < it && it <= range.last }?.let { splitIdx ->
                            splits[node]?.remove(splitIdx)
                            silentTransition2Split[petriNetAlignment.steps[splitIdx].modelMove]
                        }
                    } ?: converter.cnet.splits[node]?.single()
                    assert((split === null) == (converter.cnet.splits[node].isNullOrEmpty()))

                    with(DecoupledNodeExecution(node, join, split)) {
                        instance.getExecutionFor(this).execute()
                        causalNetSteps.add(
                            step.copy(
                                modelMove = this,
                                modelState = instance.currentState.copy()
                            )
                        )
                    }
                } else {
                    assert(step.modelMove.isSilent)
                    inboundSilentTransition2Node[step.modelMove]?.let { node ->
                        nextInstance.computeIfAbsent(node.name) { ArrayDeque() }.addLast(node)
                    }
                        ?: assert(step.modelMove in silentTransition2Join || step.modelMove in silentTransition2Split || step.modelMove in outboundSilentTransition2Node)
                    //Moves in silentTransition2Join and silentTransition2Split are handled earlier; moves in outboundSilentTransition2Node are a byproduct of transformation and can be ignored altogether
                }
            } else {
                causalNetSteps.add(step.copy(modelState = instance.currentState.copy()))
            }
        }
        return Alignment(causalNetSteps, causalNetSteps.sumOf(penalty::calculate))
    }
}

class CausalNetAsPetriNetAlignerFactory(private val base: AlignerFactory) : AlignerFactory {
    override fun invoke(model: ProcessModel, penalty: PenaltyFunction, pool: ExecutorService): Aligner {
        require(model is CausalNet)
        val converter = CausalNet2PetriNet(model)
        return CausalNetAsPetriNetAligner(base(converter.toPetriNet(), penalty, pool), converter)
    }
}