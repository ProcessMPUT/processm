package processm.experimental.conceptdrift

import processm.conformance.conceptdrift.DriftDetectorTestBase
import kotlin.test.Ignore

@Ignore("KL-Divergence doesn't work too well in this application")
class KLDivergenceDriftDetectorTest : DriftDetectorTestBase() {
    override fun detector() = KLDivergenceDriftDetector()
}