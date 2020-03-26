package processm.core.models.processtree

class RedoLoop(vararg nodes: Node) : Node(*nodes) {
    override val symbol: String
        get() = "⟲"
}