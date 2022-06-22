package processm.experimental.conceptdrift

import processm.conformance.conceptdrift.BoundStatisticalDistanceDriftDetector
import processm.conformance.conceptdrift.DriftDetectorTestBase
import processm.experimental.conceptdrift.statisticaldistance.RenyiDivergence


class RenyiDivergenceDriftDetectorTest : DriftDetectorTestBase() {
    override fun detector() = BoundStatisticalDistanceDriftDetector(RenyiDivergence(.5)::naive, slack = 1.25)

}