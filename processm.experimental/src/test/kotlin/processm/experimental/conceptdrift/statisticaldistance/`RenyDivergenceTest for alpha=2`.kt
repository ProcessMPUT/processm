package processm.experimental.conceptdrift.statisticaldistance

import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.numerical.integration.Integrator
import processm.conformance.conceptdrift.statisticaldistance.DivergenceTest

class `RenyDivergenceTest for alpha=2` : DivergenceTest() {
    override var divergence: (ContinuousDistribution, ContinuousDistribution, Integrator) -> Double =
        RenyiDivergence(2.0)::invoke
}