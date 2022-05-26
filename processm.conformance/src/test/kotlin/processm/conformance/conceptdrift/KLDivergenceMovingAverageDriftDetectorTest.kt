package processm.conformance.conceptdrift

import kotlin.test.Ignore

@Ignore("KLDivergenceMovingAverageDriftDetector is not developed enough to warrant testing")
internal class KLDivergenceMovingAverageDriftDetectorTest : DriftDetectorTestBase() {
    override fun detector() = KLDivergenceMovingAverageDriftDetector(windowSize = 25)
}