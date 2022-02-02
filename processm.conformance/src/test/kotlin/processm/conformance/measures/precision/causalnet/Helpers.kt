package processm.conformance.measures.precision.causalnet

import processm.core.helpers.HashMapWithDefault
import processm.core.helpers.mapToSet
import processm.core.log.Event
import processm.core.log.attribute.StringAttr
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.verifiers.CausalNetVerifier
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertTrue


operator fun Trace.times(n: Int): Sequence<Trace> = sequence {
    for (i in 0 until n)
        yield(this@times)
}


// http://realtimecollisiondetection.net/blog/?p=89
fun assertDoubleEquals(expected: Double, actual: Double, prec: Double = 1e-3) =
    assertTrue(
        abs(expected - actual) <= prec * max(max(1.0, abs(expected)), abs(actual)),
        "Expected: $expected, actual: $actual, prec: $prec"
    )


fun event(name: String): Event =
    Event(mutableMapOf("concept:name" to StringAttr("concept:name", name)))

fun trace(vararg nodes: Node): Trace =
    Trace(nodes.asList().map { event(it.name) }.asSequence())

fun logFromModel(model: CausalNet): Log = Log(CausalNetVerifier()
    .verify(model)
    .validLoopFreeSequences
    .mapToSet { seq -> seq.map { it.a } }
    .map { seq -> Trace(seq.asSequence().map { event(it.name) }) }
    .asSequence())

fun testPossible(model: CausalNet, maxSeqLen: Int = Int.MAX_VALUE, maxPrefixLen: Int = Int.MAX_VALUE) {
    val validSequences = CausalNetVerifierImpl(model)
        .computeSetOfValidSequences(false) { it, _ -> it.size < maxSeqLen }
        .map { it.mapNotNull { if (!it.a.isSilent) it.a else null } }.toList()
    val prefix2possible = HashMapWithDefault<List<Node>, HashSet<Node>>() { HashSet() }
    for (seq in validSequences) {
        for (i in 0 until min(seq.size, maxPrefixLen))
            prefix2possible[seq.subList(0, i)].add(seq[i])
    }
    val pa = CNetPerfectPrecisionAux(Log(emptySequence()), model)
    for ((prefix, expected) in prefix2possible.entries) {
        val actual = pa.possibleNext(listOf(prefix)).values.single()
        assertEquals(expected, actual, "prefix=$prefix expected=$expected actual=$actual")
    }
}