package processm.conformance.conceptdrift.numerical.optimization

class GradientDescentUnivariateTest : OptimizerTest() {
    override fun instance(): Optimizer = GradientDescent()
}