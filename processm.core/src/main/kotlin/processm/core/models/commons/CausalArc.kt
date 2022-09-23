package processm.core.models.commons

/**
 * A causal arc between [source] and [target] in a [ProcessModel]
 *
 * It is explicitly given in Causal Nets by dependencies (and thus implemented by [processm.core.models.causalnet.Dependency]),
 * and semi-explicitly in Petri Nets.
 */
interface CausalArc {
    val source: Activity
    val target: Activity
}