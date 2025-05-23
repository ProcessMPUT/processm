package processm.conformance.measures

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.metadata.URN
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Token
import processm.core.models.petrinet.Transition
import java.util.*

/**
 * The four counters used by [TokenBasedFitness]
 *
 * @param p Number of tokens produced (incl. [PetriNet.initialMarking])
 * @param c Number of tokens consumed (incl. [PetriNet.finalMarking])
 * @param m Number of tokens missing, i.e., not produced before they were consumed
 * @param r Number of tokens remaining in the model, i.e., produced but not consumed
 */
data class TokenCounters(val p: Int, val c: Int, val m: Int, val r: Int) {
    init {
        require(m <= c)
        require(r <= p)
    }

    val fitness: Double
        get() = (2.0 - m.toDouble() / c - r.toDouble() / p) / 2.0
}

/**
 * Computing fitness using token replay, as described in the PM book chapter 8.2
 */
class TokenBasedFitness(val model: PetriNet) : Measure<Log, Double> {

    companion object {
        val URN = URN("urn:processm:measures/token_based_fitness")
    }

    override val URN: URN
        get() = TokenBasedFitness.URN

    private val activities = model.activities.filter { !it.isSilent }.groupBy { it.name }

    /**
     * Run token replay for the given [Trace] and return the resulting counters
     */
    fun tokenReplay(trace: Trace): TokenCounters {
        var p = 0
        var c = 0
        var m = 0
        val instance = model.createInstance()
        p += model.initialMarking.values.sumOf { it.size }
        for (event in trace.events) {
            val activity = activities[event.conceptName]?.singleOrNull()
            if (activity !== null) {
                check(activity is Transition)
                for (place in activity.inPlaces) {
                    instance.currentState.compute(place) { _, old ->
                        c += 1
                        if (old === null || old.isEmpty()) {
                            m += 1
                            return@compute null
                        } else {
                            assert(old.isNotEmpty())
                            old.removeFirst()
                            return@compute old
                        }
                    }
                }
                val sharedToken = Token(activity) // tokens are immutable and do not have identity, so can be shared
                for (place in activity.outPlaces) {
                    instance.currentState.compute(place) { _, old ->
                        p += 1
                        return@compute (old ?: ArrayDeque()).apply { addLast(sharedToken) }
                    }
                }
            } else {
                // this block is intentionally left empty to skip events that have no counterparts in the model
            }
        }
        for ((place, n) in model.finalMarking) {
            instance.currentState.compute(place) { _, old ->
                val avail = old.orEmpty()
                c += n.size
                if (avail.size < n.size) {
                    m += n.size - avail.size
                    return@compute null
                } else
                    return@compute old!!.apply { repeat(n.size) { removeFirst() } }
            }
        }
        val r = instance.currentState.values.sumOf { it.size }
        return TokenCounters(p, c, m, r)
    }

    /**
     * Run [tokenReplay] for all traces in the given log [artifact] and return the sums of the counters
     */
    fun tokenReplay(artifact: Log): TokenCounters {
        var c = 0
        var m = 0
        var r = 0
        var p = 0
        for (trace in artifact.traces) {
            val counters = tokenReplay(trace)
            c += counters.c
            m += counters.m
            r += counters.r
            p += counters.p
        }
        return TokenCounters(p, c, m, r)
    }

    /**
     * Compute the fitness using token replay for the given [Trace]
     */
    operator fun invoke(trace: Trace): Double = tokenReplay(trace).fitness

    /**
     * Compute the fitness using token replay for the given [Log]
     */
    override fun invoke(artifact: Log): Double = tokenReplay(artifact).fitness

}
