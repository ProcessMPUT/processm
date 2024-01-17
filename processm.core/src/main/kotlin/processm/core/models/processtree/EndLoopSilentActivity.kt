package processm.core.models.processtree

/**
 * A silent activity representing that [parentLoop] is to be completed
 */
class EndLoopSilentActivity(val parentLoop: RedoLoop) : SilentActivity(true) {
    init {
        parent = parentLoop
    }
}
