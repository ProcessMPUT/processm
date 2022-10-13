package processm.conformance.conceptdrift.numerical.optimization

class GradientDescentMultivariateTest : MultivariateOptimizerTest() {
    override fun instance() = GradientDescent(eps = 1e-6)
}