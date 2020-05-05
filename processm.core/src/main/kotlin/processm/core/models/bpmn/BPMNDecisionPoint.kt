package processm.core.models.bpmn

import processm.core.models.commons.DecisionPoint
import java.util.*

/**
 * A [DecisionPoint] consisting of [BPMNDecision]s.
 * For each flow node in a BPMN model there are two decision points, one representing a join and the other representing a split.
 *
 * @param [flowNode] The flow node related to this decision point.
 */
class BPMNDecisionPoint(val flowNode: BPMNFlowNode) : DecisionPoint {
    private val internalPossibleOutcomes = HashSet<BPMNDecision>()
    override val possibleOutcomes: Set<BPMNDecision> = Collections.unmodifiableSet(internalPossibleOutcomes)
    internal fun add(dec: BPMNDecision) {
        internalPossibleOutcomes.add(dec)
    }

    internal fun add(activities: Set<BPMNFlowNode>) = add(BPMNDecision(activities, this))
}