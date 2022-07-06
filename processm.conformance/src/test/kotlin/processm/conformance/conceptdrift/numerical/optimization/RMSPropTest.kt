package processm.conformance.conceptdrift.numerical.optimization

class RMSPropTest : OptimizerTest() {
    override fun instance(): Optimizer = RMSProp()
}