package processm.core.models.processtree

open class SilentActivity(override val isArtificial: Boolean = false) : ProcessTreeActivity("") {
    /**
     * Silent activity represent as τ - tau
     */
    override val symbol: String
        get() = "τ"

    override val isSilent: Boolean
        get() = true
}
