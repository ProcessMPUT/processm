package processm.conformance.conceptdrift.numerical.optimization

class GradientDescentTest : OptimizerTest() {
    override fun instance(): Optimizer = GradientDescent()
}