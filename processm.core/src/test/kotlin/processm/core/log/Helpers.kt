package processm.core.log

import processm.core.log.attribute.NullAttr
import processm.core.log.attribute.StringAttr
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.Activity
import processm.core.verifiers.CausalNetVerifier
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertTrue

object Helpers {
    fun logFromString(text: String): Log =
        Log(
            text.splitToSequence('\n')
                .filter(String::isNotBlank)
                .map { line -> Trace(line.splitToSequence(" ").filter(String::isNotEmpty).map(::event)) }
        )

    fun logFromModel(model: CausalNet): Log {
        val tmp = CausalNetVerifier().verify(model).validLoopFreeSequences.map { seq -> seq.map { it.a } }
            .toSet()
        return Log(tmp.asSequence().map { seq -> Trace(seq.asSequence().map { event(it.activity) }) })
    }

    fun event(name: String): Event = Event().apply {
        attributesInternal["concept:name"] = StringAttr("concept:name", name)
        attributesInternal["lifecycle:transition"] = StringAttr("lifecycle:transition", "complete")
        attributesInternal["concept:instance"] = NullAttr("concept:instance")
        setStandardAttributes(
            mapOf(
                "concept:name" to "concept:name",
                "lifecycle:transition" to "lifecycle:transition",
                "concept:instance" to "concept:instance"
            )
        )
    }

    /**
     * Creates a [Trace] for a given sequence of [Activity]s
     */
    fun trace(vararg activities: Activity): Trace =
        Trace(activities.asList().map { event(it.name) }.asSequence())

    fun trace(vararg activities: String): Trace =
        Trace(activities.map(this::event).asSequence())

    fun trace(vararg activities: Event): Trace =
        Trace(activities.asSequence())

    operator fun Trace.times(n: Int): Sequence<Trace> = (0 until n).asSequence().map { this@times }

    // http://realtimecollisiondetection.net/blog/?p=89
    fun assertDoubleEquals(expected: Double, actual: Double, prec: Double = 1e-3) =
        assertTrue(
            abs(expected - actual) <= prec * max(max(1.0, abs(expected)), abs(actual)),
            "Expected: $expected, actual: $actual, prec: $prec"
        )
}
