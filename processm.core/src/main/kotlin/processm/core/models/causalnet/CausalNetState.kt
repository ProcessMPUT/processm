package processm.core.models.causalnet

import com.carrotsearch.hppc.ObjectIntHashMap
import com.carrotsearch.hppc.ObjectIntMap
import com.carrotsearch.hppc.ObjectLookupContainer
import processm.core.models.commons.ProcessModelState

interface CausalNetState : ObjectIntMap<Dependency>, ProcessModelState {
    val isFresh: Boolean
    fun uniqueSet(): ObjectLookupContainer<Dependency>
    fun isNotEmpty(): Boolean
    fun containsAll(other: Collection<Dependency>): Boolean
    fun containsAll(other: CausalNetState): Boolean
}

/**
 * State is a multi-set of pending obligations (the PM book, Definition 3.10)
 *
 * Sources in rows, targets in columns, and numbers of occurrences in values.
 */
open class CausalNetStateImpl : ObjectIntHashMap<Dependency>, CausalNetState {
    constructor() : super()

    constructor(stateBefore: CausalNetState) : super(stateBefore)

    private var size: Int = 0

    override var isFresh: Boolean = true
        protected set

    // TODO this should be internal, but it currently interferes with using it in processm.experimental
    open fun execute(join: Join?, split: Split?) {
        isFresh = false
        if (join !== null) {
            check(this.containsAll(join.dependencies)) { "It is impossible to execute this join in the current state" }
            for (d in join.dependencies)
                this.remove(d, 1)
        }
        if (split !== null)
            this.addAll(split.dependencies)
    }

    override fun clear() {
        super.clear()
        isFresh = true
    }

    override fun hashCode(): Int =
        isFresh.hashCode() xor super.hashCode()

    override fun equals(other: Any?): Boolean =
        other is CausalNetStateImpl && isFresh == other.isFresh && super.equals(other)

    override fun copy(): ProcessModelState = CausalNetStateImpl(this).also { it.isFresh = this.isFresh }

    override fun containsAll(other: Collection<Dependency>): Boolean =
        this.size() >= other.size && other.all { containsKey(it) }

    override fun containsAll(other: CausalNetState): Boolean =
        this.size() >= other.size() && other.all { it.value <= getOrDefault(it.key, Int.MIN_VALUE) }

    override fun addTo(key: Dependency?, incrementValue: Int): Int {
        size += incrementValue
        return super.addTo(key, incrementValue)
    }

    fun addAll(collection: Collection<Dependency>) {
        for (item in collection) {
            addTo(item, 1)
        }
    }

    fun remove(item: Dependency, count: Int): Int {
        val resCount = this.addTo(item, -count)
        if (resCount <= 0)
            this.remove(item)
        return resCount + count
    }

    override fun uniqueSet(): ObjectLookupContainer<Dependency> = this.keys()

    override fun isNotEmpty(): Boolean = !this.isEmpty

    override fun size(): Int {
        assert(size >= super.size())
        assert(size == values().sumOf { it.value })
        return size
    }
}
