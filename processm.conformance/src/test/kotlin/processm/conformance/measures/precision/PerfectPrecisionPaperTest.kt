package processm.conformance.measures.precision

import processm.conformance.measures.precision.causalnet.assertDoubleEquals
import processm.core.log.Helpers
import processm.core.models.petrinet.petrinet
import kotlin.test.Test

/**
 * Unless otherwise noted all models and values are from [1]
 *
 * [1] van der Aalst, W., Adriansyah, A. and van Dongen, B. (2012), Replaying history on process models for conformance
 * checking and performance analysis. WIREs Data Mining Knowl Discov, 2: 182-192. https://doi.org/10.1002/widm.1045
 */
class PerfectPrecisionPaperTest {

    private operator fun String.times(n: Int): String {
        val sb = StringBuilder()
        for (i in 0 until n)
            sb.append(this)
        return sb.toString()
    }

    private val log =
        Helpers.logFromString(
            ("a c d e h\n" * 455) +
                    ("a b d e g\n" * 191) +
                    ("a d c e h\n" * 177) +
                    ("a b d e h\n" * 144) +
                    ("a c d e g\n" * 111) +
                    ("a d c e g\n" * 82) +
                    ("a d b e h\n" * 56) +
                    ("a c d e f d b e h\n" * 47) +
                    ("a d b e g\n" * 38) +
                    ("a c d e f b d e h\n" * 33) +
                    ("a c d e f b d e g\n" * 14) +
                    ("a c d e f d b e g\n" * 11) +
                    ("a d c e f c d e h\n" * 9) +
                    ("a d c e f d b e h\n" * 8) +
                    ("a d c e f b d e g\n" * 5) +
                    ("a c d e f b d e f d b e g\n" * 3) +
                    ("a d c e f d b e g\n" * 2) +
                    ("a d c e f b d e f b d e g\n" * 2) +
                    ("a d c e f d b e f b d e h\n") +
                    ("a d b e f b d e f d b e g\n") +
                    ("a d c e f d b e f c d e f d b e g\n")
        )


    @Test
    fun m1() {
        val model = petrinet {
            P tout "a"
            P tin "a" * "f" tout "b" * "c"
            P tin "a" * "f" tout "d"
            P tin "b" * "c" tout "e"
            P tin "d" tout "e"
            P tin "e" tout "g" * "h" * "f"
            P tin "g" * "h"
        }
        val prec = PerfectPrecision(model)(log)
        assertDoubleEquals(0.97, prec, 0.005)
    }

    @Test
    fun m2() {
        val model = petrinet {
            P tout "a"
            P tin "a" tout "c"
            P tin "c" tout "d"
            P tin "d" tout "e"
            P tin "e" tout "h"
            P tin "h"
        }
        val prec = PerfectPrecision(model)(log)
        assertDoubleEquals(1.0, prec, 0.005)
    }

    @Test
    fun m3() {
        val model = petrinet {
            P tout "a"
            P tin "a" * "b" * "c" * "d" * "e" * "f" tout "b" * "c" * "d" * "e" * "f" * "g" * "h"
            P tin "g" * "h"
        }
        val prec = PerfectPrecision(model)(log)
        assertDoubleEquals(0.41, prec, 0.005)
    }
}