package processm.conformance.measures.precision

import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelInstance


class PerfectPrecision(model: ProcessModel) : AbstractPrecision(model) {

    init {
        require(model !is CausalNet) { "PerfectPrecision cannot handle causal nets. Use processm.conformance.measures.precision.causalnet.CNetPerfectPrecision instead." }
    }

    override fun availableActivities(prefix: List<Activity>): Set<Activity> =
        availableActivities(prefix, model.createInstance()).toSet()

    private fun availableActivities(prefix: List<Activity>, instance: ProcessModelInstance): Sequence<Activity> =
        sequence {
            if (prefix.isEmpty())
                yieldAll(instance.availableActivities.filter { !it.isSilent }.toSet())
            else {
                val first = prefix[0]
                val rest = prefix.subList(1, prefix.size)
                val originalState = instance.currentState.copy()
                for (activity in instance.availableActivities) {
                    if (activity.isSilent) {
                        instance.setState(originalState.copy())
                        instance.getExecutionFor(activity).execute()
                        yieldAll(availableActivities(prefix, instance))
                    } else if (first == activity) {
                        instance.setState(originalState.copy())
                        instance.getExecutionFor(activity).execute()
                        yieldAll(availableActivities(rest, instance))
                    }
                }
            }
        }


}