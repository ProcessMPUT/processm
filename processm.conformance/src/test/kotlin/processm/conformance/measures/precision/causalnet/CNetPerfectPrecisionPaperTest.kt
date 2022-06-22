package processm.conformance.measures.precision.causalnet

import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.CausalNetAsPetriNetAligner
import processm.conformance.models.alignments.petrinet.DecompositionAligner
import processm.core.log.Helpers.assertDoubleEquals
import processm.core.models.petrinet.converters.CausalNet2PetriNet
import kotlin.test.Test

class CNetPerfectPrecisionPaperTest : PaperTest() {

    @Test
    fun `model1 precision - AStar for CausalNet`() {
        val aligner = AStar(model1)
        assertDoubleEquals(model1Precision, CNetPerfectPrecision(model1)(aligner.align(log)))
    }

    @Test
    fun `model1 precision - AStar for PetriNet`() {
        val converter = CausalNet2PetriNet(model1)
        val aligner = CausalNetAsPetriNetAligner(AStar(converter.toPetriNet()), converter)
        assertDoubleEquals(model1Precision, CNetPerfectPrecision(model1)(aligner.align(log)))
    }

    @Test
    fun `model1 precision - DecompositionAligner`() {
        val converter = CausalNet2PetriNet(model1)
        val aligner = CausalNetAsPetriNetAligner(DecompositionAligner(converter.toPetriNet()), converter)
        assertDoubleEquals(model1Precision, CNetPerfectPrecision(model1)(aligner.align(log)))
    }

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
