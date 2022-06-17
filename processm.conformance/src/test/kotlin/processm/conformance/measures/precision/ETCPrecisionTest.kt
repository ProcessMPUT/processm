package processm.conformance.measures.precision

import processm.conformance.measures.precision.causalnet.assertDoubleEquals
import processm.core.log.Helpers
import processm.core.models.petrinet.petrinet
import kotlin.test.Test

/**
 * (MGC2010) Jorge Munoz-Gama, Josep Carmona: A Fresh Look at Precision in Process Conformance. BPM 2010: 211-226
 */
class ETCPrecisionTest {

    companion object {
        private fun stripHashSuffix(name: String): String {
            val i = name.lastIndexOf('#')
            return if (i > 0) name.substring(0, i) else name
        }
    }

    @Test
    fun `MGC2010 fig 1`() {
        val log = Helpers.logFromString(
            """
            a b d e a
            a c d g h f a
            a c g d h f a
            a c g h d f a
        """.trimIndent()
        )
        val model = petrinet {
            namePostprocessor = ::stripHashSuffix
            P tout "a#1" //p0
            P tin "a#1" tout "b" * "c" //p1
            P tin "b" * "c" tout "d" //p2
            P tin "c" * "g" tout "g" * "h" //p3
            P tin "b" tout "e" //p4
            P tin "d" tout "e" * "f" //p5
            P tin "h" tout "f" //p6
            P tin "e" * "f" tout "a#2" //p7
            P tin "a#2" //p8
        }
        val prec = ETCPrecision(model)(log)
        assertDoubleEquals(0.81, prec, 0.005)
    }

    @Test
    fun `MGC2010 fig 4a`() {
        val log = Helpers.logFromString(
            """
            a b e a
            a c h f a
            a c g h f a
        """.trimIndent()
        )
        val model = petrinet {
            namePostprocessor = ::stripHashSuffix
            P tout "a"
            P tin "a" tout "b" * "c"
            P tin "b" tout "e"
            P tin "c" tout "_1" * "g"
            P tin "_1" * "g" tout "h"
            P tin "h" tout "f"
            P tin "e" * "f" tout "a#1"
            P tin "a#1"
        }
        val prec = ETCPrecision(model)(log)
        assertDoubleEquals(1.0, prec)
    }

    @Test
    fun `MGC2010 fig 4b`() {
        val log = Helpers.logFromString(
            """
            a b e a
            a c h f a
            a c g h f a
        """.trimIndent()
        )
        val model = petrinet {
            namePostprocessor = ::stripHashSuffix

            P tout "a#1"
            P tin "a#1" tout "b" * "c"
            P tin "b" tout "e"
            P tin "c" tout "h#1" * "g"
            P tin "g" tout "h#2"
            P tin "h#1" * "h#2" tout "f"
            P tin "e" * "f" tout "a#2"
            P tin "a#2"
        }
        val prec = ETCPrecision(model)(log)
        assertDoubleEquals(1.0, prec)
    }

    @Test
    fun `MGC2010 fig 5`() {
        val log = Helpers.logFromString(
            """
            a b e a
            a c g h f a
        """.trimIndent()
        )
        val model = petrinet {
            namePostprocessor = ::stripHashSuffix

            P tout "a#1"
            P tin "a#1" tout "b" * "c"
            P tin "b" tout "e"
            P tin "c" tout "g"
            P tin "g" tout "h"
            P tin "h" tout "f"
            P tin "e" * "f" tout "a#2"
            P tin "a#2"
        }
        val prec = ETCPrecision(model)(log)
        assertDoubleEquals(1.0, prec)
    }
}