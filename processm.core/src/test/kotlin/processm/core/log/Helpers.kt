package processm.core.log

import processm.core.log.attribute.*
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

    private fun wrap(key: String, value: Any): Attribute<*> = when (value) {
        is Boolean -> BoolAttr(key, value)
        is String -> StringAttr(key, value)
        is Int -> IntAttr(key, value.toLong())
        is Long -> IntAttr(key, value)
        is Double -> RealAttr(key, value)
        is Float -> RealAttr(key, value.toDouble())
        else -> throw IllegalArgumentException("A value of the type ${value::class} is not supported")
    }

    fun event(name: String, vararg attrs: Pair<String, Any>): Event = Event().apply {
        attributesInternal["concept:name"] = StringAttr("concept:name", name)
        attributesInternal["lifecycle:transition"] = StringAttr("lifecycle:transition", "complete")
        attributesInternal["concept:instance"] = NullAttr("concept:instance")
        for ((key, value) in attrs)
            attributesInternal[key] = wrap(key, value)
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
