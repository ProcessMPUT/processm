package processm.experimental.conceptdrift

import processm.conformance.conceptdrift.BoundStatisticalDistanceDriftDetector
import processm.conformance.conceptdrift.DriftDetectorTestBase
import processm.experimental.conceptdrift.statisticaldistance.RenyiDivergence
import kotlin.test.Ignore

@Ignore("Reniy divergence is underdeveloped and the code is left only for future reference")
class RenyiDivergenceDriftDetectorTest : DriftDetectorTestBase() {
    override fun detector() = BoundStatisticalDistanceDriftDetector(RenyiDivergence(.5)::naive, slack = 1.25)

}