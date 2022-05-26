package processm.conformance.conceptdrift.numerical.integration

class TrapezoidIntegratorTest:IntegratorTest() {
    override fun instance(): Integrator = TrapezoidIntegrator(1e-4)
}