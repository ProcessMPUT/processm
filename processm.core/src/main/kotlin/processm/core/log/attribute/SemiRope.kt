package processm.core.log.attribute

/**
 * Not really a rope, rather a recursive sequence of strings, each consisting of a prefix and a suffix.
 * While the implementation is general, the primary usage assumes the prefixes are shared between multiple instances
 * and thus it makes sense to compare their address rather than their content. If this fails, a fallback is made to
 * implementations based on converting to strings as the common denominator.
 */
class SemiRope(val left: CharSequence, val right: String) : CharSequence, Comparable<CharSequence> {
    override val length: Int = left.length + right.length

    override fun get(index: Int): Char = if (index < left.length) left[index] else right[index - left.length]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        if (endIndex <= left.length) left.subSequence(startIndex, endIndex)
        else if (startIndex >= left.length) right.subSequence(startIndex - left.length, endIndex - left.length)
        else {
            assert(startIndex < left.length)
            assert(left.length < endIndex)
            SemiRope(left.subSequence(startIndex, left.length), right.substring(0, endIndex - left.length))
        }

    override fun hashCode(): Int = 31 * left.hashCode() + right.hashCode()
    override fun compareTo(other: CharSequence): Int {
        if (other is SemiRope) {
            if (left === other.left) return right.compareTo(other.right)
        }
        return toString().compareTo(other.toString())
    }

    override fun equals(other: Any?): Boolean =
        other is SemiRope && length == other.length && ((left === other.left && right == other.right) || toString() == other.toString())

    override fun toString(): String = left.toString() + right
}