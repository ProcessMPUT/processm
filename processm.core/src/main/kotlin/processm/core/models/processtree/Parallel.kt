package processm.core.models.processtree

class Parallel(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "∧"

    override val startActivities: kotlin.sequences.Sequence<Activity>
        get() = children.asSequence().flatMap { it.startActivities }

    override val endActivities: kotlin.sequences.Sequence<Activity>
        get() = children.asSequence().flatMap { it.endActivities }

    override val isStrict: Boolean = false
}