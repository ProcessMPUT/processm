package processm.experimental.performance

import kotlin.math.max

internal class AlignmentComparator(val costThreshold: Int) : Comparator<Alignment> {

    override fun compare(a: Alignment, b: Alignment): Int {
        val costA = max(a.features[0] - costThreshold, 0.0)
        val costB = max(b.features[0] - costThreshold, 0.0)
        val v = costA.compareTo(costB)
        if (v != 0)
            return v
        for (i in 1 until a.features.size) {
            val fa = a.features[i] //.value
            val fb = b.features[i] //.value
            val v = fa.compareTo(fb)
            if (v != 0)
                return v
        }
        return 0
    }

}
