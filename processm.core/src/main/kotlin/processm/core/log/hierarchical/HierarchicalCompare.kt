package processm.core.log.hierarchical

import processm.core.log.attribute.deepEquals
import processm.helpers.zipOrThrow
import processm.logging.logger

private object HierarchicalCompare {
    val logger = logger()
}

/**
 * Compares two logs.
 * @return True if the logs equal, false otherwise.
 */
fun hierarchicalCompare(seq1: Sequence<Log>, seq2: Sequence<Log>): Boolean =
    try {
        (seq1 zipOrThrow seq2).all { (l1, l2) ->
            l1 == l2 && l1.attributesInternal.deepEquals(l2.attributesInternal)
                    && l1.traceGlobalsInternal.deepEquals(l2.traceGlobalsInternal)
                    && l1.eventGlobalsInternal.deepEquals(l2.eventGlobalsInternal)
                    && (l1.traces zipOrThrow l2.traces).all { (t1, t2) ->
                t1 == t2 && t1.attributesInternal.deepEquals(t2.attributesInternal)
                        && (t1.events zipOrThrow t2.events).all { (e1, e2) ->
                    e1 == e2 && e1.attributesInternal.deepEquals(e2.attributesInternal)
                }
            }
        }
    } catch (e: IllegalArgumentException) {
        HierarchicalCompare.logger.debug(e.message)
        false
    }
