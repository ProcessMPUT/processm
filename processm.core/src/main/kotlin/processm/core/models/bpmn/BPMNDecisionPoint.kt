package processm.core.models.bpmn

import processm.core.models.commons.DecisionPoint
import java.util.*

class BPMNDecisionPoint(val flowNode: BPMNFlowNode) : DecisionPoint {
    private val internalPossibleOutcomes = HashSet<BPMNDecision>()
    override val possibleOutcomes: Set<BPMNDecision> = Collections.unmodifiableSet(internalPossibleOutcomes)
    internal fun add(dec: BPMNDecision) {
        internalPossibleOutcomes.add(dec)
    }

    internal fun add(activities: Set<BPMNFlowNode>) = add(BPMNDecision(activities, this))
}