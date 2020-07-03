package processm.experimental.onlinehmpaper

import processm.core.log.hierarchical.Trace
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.Sequence

class ModelAnalyzer(private val model: ProcessTree) {
    /**
     * Play trace on model
     * Return [true] if trace can be played on model.
     */
    fun playTrace(trace: Trace): Boolean {
        val root = model.root
        if (root is Sequence)

        trace.events.forEach { event ->
            println(event.conceptName)
        }
        return false
    }
}
