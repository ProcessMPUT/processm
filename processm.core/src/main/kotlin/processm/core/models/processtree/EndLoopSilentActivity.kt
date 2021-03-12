package processm.core.models.processtree

/**
 * A silent activity representing that [parentLoop] is to be completed
 */
class EndLoopSilentActivity(val parentLoop: RedoLoop) : SilentActivity() {
    init {
        parent = parentLoop
    }

    override val isArtificial: Boolean
        get() = true
}
