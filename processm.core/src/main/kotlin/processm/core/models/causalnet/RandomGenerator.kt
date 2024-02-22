package processm.core.models.causalnet

import processm.core.verifiers.causalnet.ActivityBinding
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import processm.helpers.allSubsets
import processm.helpers.mapToSet
import java.util.*
import kotlin.random.Random


/**
 * A random generator of Causal net models.
 * Splits are randomly chosen between full AND, full XOR and full OR.
 * Joins are always full OR (to accomodate any choice mades while generating splits).
 * If one demands a sound model (i.e., [allowUnsound] is `false`) then splits and joins that are dead (i.e., do not participate in any valid sequence) are removed.
 * Does not generate any kind of loops.
 */
class RandomGenerator(
    private val rnd: Random,
    private val nNodes: Int = 5,
    private val nAdditionalDependencies: Int = 4,
    private val pXOR: Double = .4,
    private val pAND: Double = .4,
    private val allowUnsound: Boolean = false,
    private val allowLongDistanceDependencies: Boolean = false
) {

    private fun enforceSoundness(model: MutableCausalNet, seqs: Sequence<ActivityBinding>) {
        val usedJoins = HashSet<Join>()
        val usedSplits = HashSet<Split>()
        for (ab in seqs) {
            if (ab.i.isNotEmpty())
                usedJoins.add(Join(ab.i.mapToSet { Dependency(it, ab.a) }))
            if (ab.o.isNotEmpty())
                usedSplits.add(Split(ab.o.mapToSet { Dependency(ab.a, it) }))
        }
        model.clearDependencies()
        (usedJoins.flatMap { it.dependencies } + usedSplits.flatMap { it.dependencies })
            .forEach { model.addDependency(it) }
        model.clearBindings()
        usedJoins.forEach { model.addJoin(it) }
        usedSplits.forEach { model.addSplit(it) }
    }

    private fun enforceSoundness(model: MutableCausalNet) {
        // use CausalNetVerifierImpl instead of CausalNetVerifier because the later is eager and performs soundness verification
        enforceSoundness(
            model,
            CausalNetVerifierImpl(model).validLoopFreeSequencesWithArbitrarySerialization.flatten()
        )
    }

    private val nodes = List(nNodes) { Node((it + 'a'.toInt()).toChar().toString()) }

    private fun withLongDistanceDependencies(model: MutableCausalNet) {
        for (i in 0 until nAdditionalDependencies) {
            while (true) {
                val x = rnd.nextInt(0, nNodes - 1)
                val y = rnd.nextInt(x + 1, nNodes)
                val dep = Dependency(nodes[x], nodes[y])
                if (model.outgoing[nodes[x]]?.contains(dep) != true) {
                    model.addDependency(dep)
                    break
                }
            }
        }
    }

    private fun withoutLongDistanceDependencies(model: MutableCausalNet) {
        val used = TreeSet(setOf(0, nNodes - 1))
        val unused = TreeSet(nodes.indices.toSet() - used)
        while (unused.isNotEmpty()) {
            val start = (used - used.maxOrNull()!!).random(rnd)
            val end = used.tailSet(start + 1).random(rnd)
            assert(start < end)
            var avail = unused.subSet(start, end)
            if (avail.isEmpty())
                continue
            var i = start
            while (avail.isNotEmpty()) {
                val j = avail.random(rnd)
                used.add(j)
                unused.remove(j)
                model.addDependency(nodes[i], nodes[j])
                i = j
                avail = unused.subSet(j + 1, end)
            }
            model.addDependency(nodes[i], nodes[end])
        }
    }

    fun generate(): MutableCausalNet {
        val result = MutableCausalNet(start = nodes[0], end = nodes[nodes.size - 1])
        result.addInstance(*nodes.toTypedArray())
        for (i in 1 until nNodes)
            result.addDependency(nodes[i - 1], nodes[i])
        if (allowLongDistanceDependencies)
            withLongDistanceDependencies(result)
        else
            withoutLongDistanceDependencies(result)
        for (n in nodes) {
            val o = result.outgoing[n]
            if (o != null) {
                if (o.size >= 2) {
                    val p = rnd.nextDouble(0.0, 1.0)
                    if (p <= pAND)
                        result.addSplit(Split(o))  //AND
                    else if (p - pAND <= pXOR)
                        o.forEach { result.addSplit(Split(setOf(it))) }    //XOR
                    else
                        o.allSubsets(true).forEach { result.addSplit(Split(it.toSet())) }   //OR
                } else
                    result.addSplit(Split(o))

            }
            val i = result.incoming[n]
            i?.allSubsets(true)?.forEach { result.addJoin(Join(it.toSet())) }
        }
        if (!allowUnsound)
            enforceSoundness(result)
        return result
    }
}
