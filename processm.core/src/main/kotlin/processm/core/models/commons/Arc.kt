package processm.core.models.commons

/**
 * A causal arc between [source] and [target] in a [ProcessModel]
 */
data class Arc(override val source: Activity, override val target: Activity) : CausalArc
