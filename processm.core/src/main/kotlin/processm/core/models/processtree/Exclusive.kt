package processm.core.models.processtree

class Exclusive(vararg nodes: Node) : Node(*nodes) {
    override val symbol: String
        get() = "×"
}