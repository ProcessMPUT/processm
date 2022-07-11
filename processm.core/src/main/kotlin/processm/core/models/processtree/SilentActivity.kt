package processm.core.models.processtree

open class SilentActivity(
    @Deprecated("Use isSilent instead", replaceWith = ReplaceWith("isSilent"))
    override val isArtificial: Boolean = false
) : ProcessTreeActivity("") {
    /**
     * Silent activity represent as τ - tau
     */
    override val symbol: String
        get() = "τ"

    override val isSilent: Boolean
        get() = true
}
