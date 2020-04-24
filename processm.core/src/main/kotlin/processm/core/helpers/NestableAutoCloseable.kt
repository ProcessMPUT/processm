package processm.core.helpers

import processm.core.logging.logger
import processm.core.logging.trace

/**
 * A decorator on an [AutoCloseable] that allows for nested [use] calls. The underlying object is initialized in the
 * topmost [use] call using the constructor-provided [initializer]. The [AutoCloseable.close] method is called on exit
 * from the topmost call to [use]. The exceptions thrown by the [initializer] and the callee provided as an argument
 * to the [use] function are left unhandled and propagated to the caller.
 *
 * @property initializer The initializer for the underlying [AutoCloseable] object. May be recalled.
 */
class NestableAutoCloseable<T : AutoCloseable>(private val initializer: () -> T) {
    companion object {
        private val logger = logger()
    }

    private var ref: T? = null
    private var counter: Byte = 0

    /**
     * Calls the given [callee] supplied with the reference to the underlying [T] object. The object is created if it
     * does not exist. The nested calls to [use] function are supported. The object is reclaimed on exit from the
     * topmost [use] call. The exceptions thrown by the [initializer] and the callee provided as an argument to the
     * [use] function are left unhandled and propagated to the caller.
     *
     * @param callee The callee to run with the [AutoCloseable] object.
     * @throws IllegalStateException If the number of nested calls reaches the maximum of [Byte.MAX_VALUE].
     */
    fun use(callee: (T) -> Unit) {
        // Note that the .toByte() calls are evaluated at compile-time. See bytecode for details.
        assert(counter >= 0.toByte())
        check(counter < Byte.MAX_VALUE) { "Reached the maximum of ${Byte.MAX_VALUE} of the nested calls." }
        assert(counter == 0.toByte() && ref === null || counter > 0.toByte())

        try {
            if (counter++ == 0.toByte()) {
                logger.trace { "Initializing AutoCloseable with $initializer" }
                ref = initializer()
            }
            logger.trace { "Counter value $counter on enter to $callee" }
            callee(ref!!)
        } finally {
            logger.trace { "Counter value $counter on exit from $callee" }
            if (--counter == 0.toByte() && ref !== null) {
                // we have to check the reference above, as the initializer may throw an exception
                logger.trace { "Reclaiming AutoCloseable $ref" }
                ref!!.close()
                ref = null
            }

            assert(counter >= 0.toByte())
            assert(counter == 0.toByte() && ref === null || counter > 0.toByte())
        }
    }
}