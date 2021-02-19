package processm.experimental.performance


@ExperimentalUnsignedTypes
data class UInt128(var upper: ULong, var lower: ULong) {

    /*
    companion object {
        val ZERO = UInt128()
        val ONE = UInt128(1UL)
    }
     */

    constructor(lower: ULong = 0UL) : this(0UL, lower)
    constructor(lower: UInt) : this(lower.toULong())

    /*
    infix fun shl(n: Int) =
        if(n<ULong.SIZE_BITS)
            UInt128((upper shl n) or (lower shr (ULong.SIZE_BITS - n)), lower shl n)
        else
            UInt128(lower shl (n-ULong.SIZE_BITS), 0UL)

    infix fun or(other: UInt128) = UInt128(upper or other.upper, lower or other.lower)

    infix fun and(other: UInt128) = UInt128(upper and other.upper, lower and other.lower)
     */


    fun shl(n: Int) {
        if (n < ULong.SIZE_BITS) {
            upper = (upper shl n) or (lower shr (ULong.SIZE_BITS - n))
            lower = lower shl n
        } else {
            upper = lower shl (n - ULong.SIZE_BITS)
            lower = 0UL
        }
    }

    fun or(other: UInt128) {
        upper = upper or other.upper
        lower = lower or other.lower
    }

    fun and(other: UInt128) {
        upper = upper and other.upper
        lower = lower and other.lower
    }

    fun isZero() = (lower == 0UL) && (upper == 0UL)
    fun isOne() = (lower == 1UL) && (upper == 0UL)
}