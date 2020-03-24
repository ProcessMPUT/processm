package processm.miners.heuristicminer.bindingproviders

import processm.core.models.causalnet.Binding
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node

interface BindingProvider {
    fun computeBindings(model: Model, trace: List<Node>): List<Binding>
}