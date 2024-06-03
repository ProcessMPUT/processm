package processm.conformance.conceptdrift

import processm.conformance.conceptdrift.statisticaldistance.NaiveJensenShannonDivergence

class JensenShannonDivergenceDriftDetectorTest : DriftDetectorTestBase() {
    override fun detector() =
        BoundStatisticalDistanceDriftDetector(::NaiveJensenShannonDivergence, newFeatureIsDrift = false)

}