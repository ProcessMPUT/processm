package processm.core.models.processtree

class RedoLoop(vararg nodes: Node) : InternalNode(*nodes) {
    override val symbol: String
        get() = "⟲"
    override val startActivities: kotlin.sequences.Sequence<Activity>
        get() = children[0].startActivities

    override val endActivities: kotlin.sequences.Sequence<Activity>
        get() = children[0].endActivities

    override val isStrict: Boolean = true
}