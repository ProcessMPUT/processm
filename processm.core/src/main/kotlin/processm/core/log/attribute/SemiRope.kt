package processm.core.log.attribute

/**
 * Not really a rope, rather a recursive sequence of strings, each consisting of a prefix and a suffix.
 * While the implementation is general, the primary usage assumes the prefixes are shared between multiple instances,
 * and thus it makes sense to compare their address rather than their content. If this fails, a fallback is made to
 * implementations based on converting to strings as the common denominator.
 */
class SemiRope(val left: CharSequence, val right: String) : CharSequence, Comparable<CharSequence> {
    override val length: Int = left.length + right.length
    val depth: Int = if (left is SemiRope) left.depth + 1 else 2

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

    private fun flattenTo(target: ArrayDeque<CharSequence>): ArrayDeque<CharSequence> {
        if (right.isNotEmpty())
            target.addFirst(right)
        if (left is SemiRope)
            left.flattenTo(target)
        else {
            if (left.isNotEmpty()) target.addFirst(left)
        }
        return target
    }


    override fun compareTo(other: CharSequence): Int {
        if (other is SemiRope) {
            if (left === other.left) return right.compareTo(other.right)
            val a = flattenTo(ArrayDeque(depth))
            val b = other.flattenTo(ArrayDeque(other.depth))
            var i = 0
            var j = 0
            while (i < a.size && j < b.size) {
                if (a[i] != b[j])
                    break
                ++i
                ++j
            }
            if (i == a.size) {
                //a is a prefix of b, possibly an improper prefix, i.e., equal to b
                return if (j == b.size) 0 else -1
            }
            if (j == b.size) {
                //b is a proper prefix of a (since a==b was already handled)
                return 1
            }
            var x = 0
            var y = 0
            var aEnded = false
            var bEnded = false
            while (!aEnded && !bEnded) {
                val c = a[i][x].compareTo(b[j][y])
                if (c != 0) return c
                ++x
                while (x == a[i].length) {
                    ++i
                    if (i == a.size) {
                        aEnded = true
                        break
                    }
                    x = 0
                }
                ++y
                while (y == b[j].length) {
                    ++j
                    if (j == b.size) {
                        bEnded = true
                        break
                    }
                    y = 0
                }
            }
            assert(aEnded || bEnded)
            return bEnded.compareTo(aEnded)
        }
        return toString().compareTo(other.toString())
    }

    override fun equals(other: Any?): Boolean =
        other is SemiRope && length == other.length && ((left === other.left && right == other.right) || toString() == other.toString())

    override fun toString(): String = left.toString() + right
}