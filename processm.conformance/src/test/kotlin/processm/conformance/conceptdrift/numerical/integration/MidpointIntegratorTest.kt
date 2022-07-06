package processm.conformance.conceptdrift.numerical.integration

class MidpointIntegratorTest : IntegratorTest() {
    override fun instance(): Integrator = MidpointIntegrator(1e-4)
}