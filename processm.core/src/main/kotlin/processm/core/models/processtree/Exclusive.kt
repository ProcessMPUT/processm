package processm.core.models.processtree

class Exclusive(vararg nodes: Node) : Node(*nodes) {
    override val symbol: String
        get() = "×"

    override val startActivities: kotlin.sequences.Sequence<Activity>
        get() = children.asSequence().flatMap { it.startActivities }

    override val endActivities: kotlin.sequences.Sequence<Activity>
        get() = children.asSequence().flatMap { it.endActivities }
}