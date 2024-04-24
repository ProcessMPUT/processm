package processm.core.models.petrinet

import processm.core.models.commons.ProcessModelState
import processm.helpers.mapToArray
import java.util.*

/**
 * The marking of a Petri net. Stores the places consisting of at least one token.
 * By contract non-positive numbers of tokens are forbidden.
 */
class Marking() : HashMap<Place, Deque<Token>>(), ProcessModelState {
    companion object {
        val empty = Marking()
    }

    constructor(other: Map<Place, Deque<Token>>) : this() {
        for ((place, tokens) in other)
            if (tokens.isNotEmpty())
                put(place, ArrayDeque(tokens))
    }

    /**
     * Creates new instance of [Marking] by putting tokens into the given [places]. Token properties are not set.
     */
    constructor(vararg places: Place) : this(*places.mapToArray { it to Token(null) })

    /**
     * Creates new instance of [Marking] by putting the given number of tokens into the given [places]. Token properties
     * are not set.
     * @param _dummy Do not use; parameter included to solve JVM signature clash
     */
    constructor(places: Map<Place, Int>, _dummy: Any? = null) :
            this(*places.flatMap { (place, count) -> (1..count).map { place } }.toTypedArray())

    /**
     * Creates new instance of [Marking] by putting the given [tokens] into the given places.
     */
    constructor(vararg tokens: Pair<Place, Token>) : this() {
        for ((place, token) in tokens)
            compute(place) { _, old ->
                (old ?: ArrayDeque()).apply { this as ArrayDeque<Token>; add(token) }
            }
    }

    override fun copy(): Marking = Marking(this)

    /**
     * Two markings equal if they consist of the same numbers of tokens in the same places. The values of properties
     * of tokens are irrelevant.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Marking) return false
        if (this.size != other.size) return false

        for ((k, v) in entries) {
            if (v.size != other[k]?.size)
                return false
        }

        return true
    }

    override fun hashCode(): Int {
        var h = 0
        for ((k, v) in entries) h += k.hashCode() xor v.size.hashCode()
        return h
    }
}

