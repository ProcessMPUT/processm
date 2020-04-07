package processm.core.models.processtree

class EndLoopSilentActivity(val parentLoop: RedoLoop) : SilentActivity() {
    init {
        parent = parentLoop
    }
}