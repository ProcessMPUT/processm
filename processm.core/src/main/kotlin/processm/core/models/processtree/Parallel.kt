package processm.core.models.processtree

class Parallel(vararg nodes: Node) : Node(*nodes) {
    override val symbol: String
        get() = "∧"
}