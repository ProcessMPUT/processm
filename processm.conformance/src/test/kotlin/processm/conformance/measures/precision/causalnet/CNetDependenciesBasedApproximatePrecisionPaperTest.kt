package processm.conformance.measures.precision.causalnet

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests with regressive in their names are bottom-up - the values were computed by the implementation itself and
 * their only purpose is to spot regressions rather than check correctness
 */
class CNetDependenciesBasedApproximatePrecisionPaperTest : PaperTest() {


    @Test
    fun `model1 precision - upper bound`() {
        assertTrue { model1Precision >= CNetDependenciesBasedApproximatePrecision(model1)(log) }
    }

    @Test
    fun `model1 precision - exact regressive`() {
        assertDoubleEquals(0.663, CNetDependenciesBasedApproximatePrecision(model1)(log))
    }

    @Test
    fun `model2 precision`() {
        assertTrue { model2Precision >= CNetDependenciesBasedApproximatePrecision(model2)(log) }
    }

    @Test
    fun `model2 precision - exact regressive`() {
        assertDoubleEquals(model2Precision, CNetDependenciesBasedApproximatePrecision(model2)(log))
    }

    @Test
    fun `model3 precision`() {
        assertTrue { model3Precision >= CNetDependenciesBasedApproximatePrecision(model3)(log) }
    }

    @Test
    fun `model3 precision - exact regressive`() {
        assertDoubleEquals(0.451, CNetDependenciesBasedApproximatePrecision(model3)(log))
    }

    @Test
    fun `model4 precision`() {
        assertTrue { model4Precision >= CNetDependenciesBasedApproximatePrecision(model4)(log) }
    }

    @Test
    fun `model4 precision - exact regressive`() {
        assertDoubleEquals(model4Precision, CNetDependenciesBasedApproximatePrecision(model4)(log))
    }


}