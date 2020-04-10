package processm.core.models.processtree

open class SilentActivity : ProcessTreeActivity("") {
    /**
     * Silent activity represent as τ - tau
     */
    override val symbol: String
        get() = "τ"
}