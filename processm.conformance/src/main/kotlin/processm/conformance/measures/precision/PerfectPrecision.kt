package processm.conformance.measures.precision

import processm.core.helpers.Trie
import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelState


/**
 * An implementation of precision which is guaranteed to return an exact value, but suitable only for models with local semantics.
 *
 * If your model is a [CausalNet], use [processm.conformance.measures.precision.causalnet.CNetPerfectPrecision] instead
 */
class PerfectPrecision(model: ProcessModel) : AbstractPrecision(model) {

    init {
        require(model !is CausalNet) { "PerfectPrecision cannot handle causal nets. Use processm.conformance.measures.precision.causalnet.CNetPerfectPrecision instead." }
    }

    override fun availableActivities(prefixes: Trie<Activity, PrecisionData>) {
        val tmp = Trie<Activity, HashSet<Activity>> { HashSet() }
        for (entry in prefixes)
            tmp.getOrPut(entry.prefix)
        val stack = ArrayDeque<Pair<Trie<Activity, HashSet<Activity>>, ProcessModelState>>()
        val instance = model.createInstance()
        stack.add(tmp to instance.currentState)
        while (stack.isNotEmpty()) {
            val (trie, state) = stack.removeLast()
            instance.setState(state)
            for (activity in instance.availableActivities) {
                if (activity.isSilent) {
                    instance.setState(state.copy())
                    instance.getExecutionFor(activity).execute()
                    stack.addLast(trie to instance.currentState)
                } else {
                    trie.value.add(activity)
                    val child = trie.getOrNull(activity)
                    if (child !== null) {
                        instance.setState(state.copy())
                        instance.getExecutionFor(activity).execute()
                        stack.addLast(child to instance.currentState)
                    }
                }
            }
        }
        for (entry in prefixes) {
            val o = tmp.getOrNull(entry.prefix)?.value
            checkNotNull(o)
            entry.trie.value.available = o.size
            entry.trie.value.availableAndChild = o.count(entry.trie.children::containsKey)
        }
    }

    override fun availableActivities(prefix: List<Activity>): Set<Activity> {
        throw NotImplementedError("This function is not implemented on purpose, as PerfectPrecision reimplements availableActivities working directly on a trie")
    }
}