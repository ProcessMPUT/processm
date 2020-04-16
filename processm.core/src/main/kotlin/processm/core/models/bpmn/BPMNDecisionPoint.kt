package processm.core.models.bpmn

import processm.core.models.commons.DecisionPoint
import java.util.*
import kotlin.collections.ArrayList

class BPMNDecisionPoint(val flowNode: BPMNFlowNode) : DecisionPoint {
    private val internalPossibleOutcomes = ArrayList<BPMNDecision>()
    override val possibleOutcomes: List<BPMNDecision> = Collections.unmodifiableList(internalPossibleOutcomes)
    internal fun add(dec: BPMNDecision) {
        internalPossibleOutcomes.add(dec)
    }

    internal fun add(activities: List<BPMNFlowNode>) = add(BPMNDecision(activities, this))
}