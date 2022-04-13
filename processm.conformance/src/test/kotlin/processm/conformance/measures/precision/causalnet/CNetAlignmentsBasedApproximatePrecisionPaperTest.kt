package processm.conformance.measures.precision.causalnet

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests with regressive in their names are bottom-up - the values were computed by the implementation itself and
 * their only purpose is to spot regressions rather than check correctness
 */
class CNetAlignmentsBasedApproximatePrecisionPaperTest : PaperTest() {


    @Test
    fun `model1 precision - lower bound`() {
        assertTrue { CNetAlignmentsBasedApproximatePrecision(model1)(log) >= model1Precision }
    }

    @Test
    fun `model1 precision - exact regressive`() {
        assertDoubleEquals(0.999, CNetAlignmentsBasedApproximatePrecision(model1)(log))
    }

    @Test
    fun `model2 precision - lower bound`() {
        assertTrue { CNetAlignmentsBasedApproximatePrecision(model2)(log) >= model2Precision }
    }

    @Test
    fun `model2 precision - exact regressive`() {
        assertDoubleEquals(model2Precision, CNetAlignmentsBasedApproximatePrecision(model2)(log))
    }

    @Test
    fun `model3 precision - lower bound`() {
        assertTrue { CNetAlignmentsBasedApproximatePrecision(model3)(log) >= model3Precision }
    }

    @Test
    fun `model3 precision - exact regressive`() {
        assertDoubleEquals(1.0, CNetAlignmentsBasedApproximatePrecision(model3)(log))
    }

    @Test
    fun `model4 precision - lower bound`() {
        assertTrue { CNetAlignmentsBasedApproximatePrecision(model4)(log) >= model4Precision }
    }

    @Test
    fun `model4 precision - exact regressive`() {
        assertDoubleEquals(model4Precision, CNetAlignmentsBasedApproximatePrecision(model4)(log))
    }


}