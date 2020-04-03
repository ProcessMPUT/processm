package processm.core.models.processtree

class RedoLoop(vararg nodes: Node) : Node(*nodes) {
    override val symbol: String
        get() = "⟲"
    override val startActivities: kotlin.sequences.Sequence<Activity>
        get() = children[0].startActivities

    override val endActivities: kotlin.sequences.Sequence<Activity>
        get() = children[0].endActivities
}