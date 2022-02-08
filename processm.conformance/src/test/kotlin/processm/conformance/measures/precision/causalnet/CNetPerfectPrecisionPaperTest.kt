package processm.conformance.measures.precision.causalnet

import kotlin.test.Test

class CNetPerfectPrecisionPaperTest : PaperTest() {

    @Test
    fun `model1 precision`() {
        assertDoubleEquals(model1Precision, CNetPerfectPrecision(model1)(log))
    }

    @Test
    fun `model2 precision`() {
        assertDoubleEquals(model2Precision, CNetPerfectPrecision(model2)(log))
    }

    @Test
    fun `model3 precision`() {
        assertDoubleEquals(model3Precision, CNetPerfectPrecision(model3)(log))
    }

    @Test
    fun `model4 precision`() {
        assertDoubleEquals(model4Precision, CNetPerfectPrecision(model4)(log))
    }


}