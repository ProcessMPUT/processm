package processm.miners.onlineminer

import org.apache.commons.math3.fraction.BigFraction

/**
 * A base class for objects comparable according to a sorted list of [BigFraction]s
 */
abstract class HasFeatures : Comparable<HasFeatures> {

    abstract val features: List<BigFraction>

    override fun compareTo(other: HasFeatures): Int {
        val a = features
        val b = other.features
        assert(a.size == b.size)
        for (i in a.indices) {
            val c = a[i].compareTo(b[i])
            if (c != 0)
                return c
        }
        return 0
    }
}
