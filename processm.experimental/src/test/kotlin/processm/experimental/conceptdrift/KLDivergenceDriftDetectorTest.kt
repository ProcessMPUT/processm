package processm.experimental.conceptdrift

import processm.conformance.conceptdrift.DriftDetectorTestBase

class KLDivergenceDriftDetectorTest : DriftDetectorTestBase() {
    override fun detector() = KLDivergenceDriftDetector()
}