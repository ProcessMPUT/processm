package processm.experimental.conceptdrift

import processm.conformance.conceptdrift.BoundStatisticalDistanceDriftDetector
import processm.conformance.conceptdrift.DriftDetectorTestBase
import processm.experimental.conceptdrift.statisticaldistance.NaiveHellingerDistance
import kotlin.test.Ignore

@Ignore("Hellinger distance is finnicky")
class HellingerDistanceDriftDetectorTest : DriftDetectorTestBase() {
    override fun detector() = BoundStatisticalDistanceDriftDetector(::NaiveHellingerDistance)

}