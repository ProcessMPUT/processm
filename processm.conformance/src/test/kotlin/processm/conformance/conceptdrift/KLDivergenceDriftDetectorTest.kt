package processm.conformance.conceptdrift

class KLDivergenceDriftDetectorTest : DriftDetectorTestBase() {
    override fun detector() = KLDivergenceDriftDetector()
}