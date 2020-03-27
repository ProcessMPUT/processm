package processm.core.models.processtree

class SilentActivity : Activity("") {
    /**
     * Silent activity represent as τ - tau
     */
    override val symbol: String
        get() = "τ"
}