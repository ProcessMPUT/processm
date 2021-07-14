package processm.miners.causalnet.heuristicminer.bindingproviders

import processm.core.models.causalnet.Binding
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node

/**
 * For the given model and trace, return the set of bindings of the model that were used during the execution of the trace.
 */
interface BindingProvider {
    fun computeBindings(model: CausalNet, trace: List<Node>): List<Binding>
}