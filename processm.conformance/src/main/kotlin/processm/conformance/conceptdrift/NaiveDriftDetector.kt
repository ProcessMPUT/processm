package processm.conformance.conceptdrift

import processm.conformance.models.alignments.Alignment
import kotlin.math.absoluteValue

private val Alignment.matches
    get() = cost == 0

private class BoundedBinomialEstimator(val capacity: Int) {
    private val data = ArrayList<Boolean>(capacity)
    private var sum: Double = 0.0
    private var idx = 0

    private fun Boolean.toInt() = if (this) 1 else 0

    val isFull: Boolean
        get() = data.size == capacity

    val p: Double
        get() = sum / data.size

    fun add(element: Boolean): Boolean? {
        var old: Boolean? = null
        if (isFull) {
            old = data[idx]
            sum -= data[idx].toInt()
            data[idx] = element
        } else
            data.add(element)
        sum += element.toInt()
        idx = (idx + 1) % capacity
        return old
    }
}

class NaiveDriftDetector(val windowSize: Int, val threshold: Double) : DriftDetector<Alignment, List<Alignment>> {
    override var drift: Boolean = false
        private set

    private val before = BoundedBinomialEstimator(windowSize)
    private val after = BoundedBinomialEstimator(windowSize)

    override fun fit(artifact: List<Alignment>) {
        (if (artifact.size < 2 * windowSize) artifact else artifact.takeLast(2 * windowSize)).forEach(::observe)
    }

    override fun observe(artifact: Alignment): Boolean {
        if (before.isFull)
            after.add(artifact.matches)?.let { before.add(it) }
        else
            before.add(artifact.matches)
        if (after.isFull) {
            assert(before.isFull)
            val pbefore = before.p
            val pafter = after.p
            drift = (pbefore - pafter).absoluteValue > threshold
        }
        return drift
    }
}