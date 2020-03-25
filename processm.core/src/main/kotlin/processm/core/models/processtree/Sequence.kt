package processm.core.models.processtree

class Sequence(vararg nodes: Node) : Node(*nodes) {
    override val symbol: String
        get() = "→"
}