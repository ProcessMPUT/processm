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

    private val transition2Node: Map<Transition, Node>
        get() = converter.node2Transition.inverseBidiMap()

    override fun align(trace: Trace): Alignment = translate(base.align(trace))

    override fun align(log: Log, summarizer: EventsSummarizer<*>?): Sequence<Alignment> =
        base.align(log, summarizer).map(this::translate)

    override fun align(log: Sequence<Trace>, summarizer: EventsSummarizer<*>?): Sequence<Alignment> =
        base.align(log, summarizer).map(this::translate)

    private fun translate(petriNetAlignment: Alignment): Alignment {
        for ((idx, petriNetStep) in petriNetAlignment.steps.withIndex()) {
            val modelMove = petriNetStep.modelMove
            val modelMoveAsSplit = silentTransition2Split[modelMove]
            val modelMoveAsJoin = silentTransition2Join[modelMove]
            val modelMoveAsNode = transition2Node[modelMove]
            logger.debug { "$idx $modelMove $modelMoveAsJoin $modelMoveAsNode $modelMoveAsSplit " }
        }
        val causalNetSteps = ArrayList<Step>()
        val instance = converter.cnet.createInstance()

        val ranges = ArrayList<Pair<Int, Int>?>()
        val last = HashMap<Node, Int>()
        val splits = HashMap<Node, ArrayList<Int>>()
        val joins = HashMap<Node, ArrayList<Int>>()

        for ((idx, step) in petriNetAlignment.steps.withIndex()) {
            val node = transition2Node[step.modelMove]
            if (node !== null) {
                val lastIdx = last[node]
                if (lastIdx !== null)
                    ranges[lastIdx] = Pair(ranges[lastIdx]!!.first, idx)
                ranges.add(Pair(lastIdx ?: 0, petriNetAlignment.steps.size))
                last[node] = idx
            } else {
                ranges.add(null)
                val split = silentTransition2Split[step.modelMove]
                if (split !== null)
                    splits.computeIfAbsent(split.source) { ArrayList() }.add(idx)
                val join = silentTransition2Join[step.modelMove]
                if (join !== null)
                    joins.computeIfAbsent(join.target) { ArrayList() }.add(idx)
            }
        }
        for ((idx, step) in petriNetAlignment.steps.withIndex()) {
            logger.debug { "$idx $step" }
            if (step.modelMove !== null) {
                val node = transition2Node[step.modelMove]
                if (node !== null) {
                    val range = ranges[idx]
                    logger.debug { "node = $node range = $range joins = ${joins[node]} splits = ${splits[node]}" }
                    checkNotNull(range)
                    val joinIdx = joins[node]?.filter { range.first <= it && it < idx }?.minOrNull()
                    val join = if (joinIdx !== null) {
                        joins[node]?.remove(joinIdx)
                        silentTransition2Join[petriNetAlignment.steps[joinIdx].modelMove]
                    } else converter.cnet.joins[node]?.single()
                    logger.debug { "joinIdx=$joinIdx join=$join" }
                    check((join === null) == (converter.cnet.joins[node].isNullOrEmpty()))
                    val splitIdx = splits[node]?.filter { idx < it && it <= range.second }?.minOrNull()
                    val split = if (splitIdx !== null) {
                        splits[node]?.remove(splitIdx)
                        silentTransition2Split[petriNetAlignment.steps[splitIdx].modelMove]
                    } else converter.cnet.splits[node]?.single()
                    logger.debug { "splitIdx=$splitIdx split=$split" }
                    check((split === null) == (converter.cnet.splits[node].isNullOrEmpty()))
                    val cnetModelMove = DecoupledNodeExecution(node, join, split)
                    logger.debug { "cnetModelMove=$cnetModelMove" }
                    instance.getExecutionFor(cnetModelMove).execute()
                    causalNetSteps.add(
                        step.copy(
                            modelMove = cnetModelMove,
                            modelState = instance.currentState.copy()
                        )
                    )
                } else {
                    assert(step.modelMove.isSilent)
                    assert(silentTransition2Join[step.modelMove] !== null || silentTransition2Split[step.modelMove] !== null)
                    //skip altogether, it is handled by the code in the "then" branch
                }
            } else {
                causalNetSteps.add(step.copy(modelState = instance.currentState.copy()))
            }
        }
        return Alignment(causalNetSteps, petriNetAlignment.cost)
    }
}

class CausalNetAsPetriNetAlignerFactory(private val base: AlignerFactory) : AlignerFactory {
    override fun invoke(model: ProcessModel, penalty: PenaltyFunction, pool: ExecutorService): Aligner {
        require(model is CausalNet)
        val converter = CausalNet2PetriNet(model)
        return CausalNetAsPetriNetAligner(base(converter.toPetriNet(), penalty, pool), converter)
    }
}