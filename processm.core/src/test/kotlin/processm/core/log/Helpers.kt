package processm.core.log

import processm.core.helpers.identityMap
import processm.core.log.attribute.*
import processm.core.log.attribute.Attribute.CONCEPT_INSTANCE
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.LIFECYCLE_TRANSITION
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.Activity
import processm.core.verifiers.CausalNetVerifier
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertEquals
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

    fun event(name: String, vararg attrs: Pair<String, Any>): Event = Event(mutableAttributeMapOf(*attrs)).apply {
        attributesInternal.computeIfAbsent(CONCEPT_NAME) { name }
        attributesInternal.computeIfAbsent(LIFECYCLE_TRANSITION) { "complete" }
        attributesInternal.computeIfAbsent(CONCEPT_INSTANCE) { null }
        setStandardAttributes(identityMap())
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
    fun assertDoubleEquals(expected: Double?, actual: Double?, prec: Double = 1e-3) {
        if (expected != null && actual != null)
            assertTrue(
                abs(expected - actual) <= prec * max(max(1.0, abs(expected)), abs(actual)),
                "Expected: $expected, actual: $actual, prec: $prec"
            )
        else
            assertEquals(expected, actual)
    }
}
