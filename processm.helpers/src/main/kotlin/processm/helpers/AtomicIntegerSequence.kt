package processm.helpers

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.IntUnaryOperator

/**
 * An atomic sequence of integer values in range [start] to [endInclusive].
 * However, both [hasNext] and [next] are internally atomic, there is no guarantee of atomicity for calling both of them
 * in order. To ensure atomicity of successive [hasNext] and [next] calls use an external synchronization mechanism.
 *
 * Once initialized, the object of this class does not allocate memory. [iterator] creates an interface-featured wrapper
 * on this object for the use in the contexts where [Iterator] or [ClosedRange] is required. The wrapper allocates
 * memory due to boxing of primitive values.
 *
 * @param start The minimum value (inclusive)
 * @param endInclusive The maximum value (inclusive)
 * @param allowOverflow Controls whether integer overflow is allowed. If true the sequence wraps around at [max] to [start] value.
 *
 * @throws IllegalArgumentException If [start] > [endInclusive].
 */
class AtomicIntegerSequence(
    val start: Int,
    val endInclusive: Int,
    val allowOverflow: Boolean = false,
) {
    init {
        require(start <= endInclusive) { "start must be <= endInclusive: $start and $endInclusive given" }
    }

    private val current = AtomicInteger(start)
    private var overflowed = false

    /**
     * The actual update function. Wrapped once into [IntUnaryOperator] to prevent re-allocations.
     */
    private val update: IntUnaryOperator = IntUnaryOperator { current: Int ->
        when {
            overflowed -> throw ArithmeticException("Integer overflow")
            current < endInclusive -> current + 1
            allowOverflow -> start
            else -> {
                overflowed = true
                Int.MIN_VALUE
            }
        }
    }

    /**
     * Returns integers from the given range atomically. There is no need for synchronization.
     *
     * @throws ArithmeticException If overflow occurs.
     */
    fun next(): Int = current.getAndUpdate(update)

    /**
     * Returns atomically whether this sequence has more integers.
     */
    fun hasNext(): Boolean = !overflowed

    fun iterator() = AtomicIntegerSequenceObjectWrapper(this)
}

/**
 *  An interface-featured wrapper for [AtomicIntegerSequence]. Enables the use of [AtomicIntegerSequence] wherever
 *  [Iterator] or [ClosedRange] are required. The use of wrapper incurs a boxing/allocation penalty.
 */
@JvmInline
value class AtomicIntegerSequenceObjectWrapper internal constructor(private val base: AtomicIntegerSequence) :
    ClosedRange<Int>, Iterator<Int> {
    override val start: Int
        get() = base.start
    override val endInclusive: Int
        get() = base.endInclusive

    override fun hasNext(): Boolean = base.hasNext()
    override fun next(): Int = base.next()
}
