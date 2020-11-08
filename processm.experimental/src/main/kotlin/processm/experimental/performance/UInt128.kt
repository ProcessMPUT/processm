package processm.experimental.performance


@ExperimentalUnsignedTypes
data class UInt128(val upper: ULong, val lower: ULong) {

    companion object {
        val ZERO = UInt128()
        val ONE = UInt128(1UL)
    }

    constructor(lower: ULong = 0UL) : this(0UL, lower)
    constructor(lower: UInt) : this(lower.toULong())

    infix fun shl(n: Int) =
        if(n<ULong.SIZE_BITS)
            UInt128((upper shl n) or (lower shr (ULong.SIZE_BITS - n)), lower shl n)
        else
            UInt128(lower shl (n-ULong.SIZE_BITS), 0UL)

    infix fun or(other: UInt128) = UInt128(upper or other.upper, lower or other.lower)

    infix fun and(other: UInt128) = UInt128(upper and other.upper, lower and other.lower)
}