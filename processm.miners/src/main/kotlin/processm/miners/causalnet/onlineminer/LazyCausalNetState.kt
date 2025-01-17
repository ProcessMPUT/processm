package processm.miners.causalnet.onlineminer

import processm.core.models.causalnet.CausalNetState
import processm.core.models.causalnet.CausalNetStateImpl
import processm.core.models.causalnet.Dependency
import processm.core.models.commons.ProcessModelState
import processm.helpers.MutableMultiSet
import java.lang.ref.SoftReference
import java.util.*

/**
 * A lazily evaluated [CausalNetState].
 *
 * The information is stored as a [SoftReference] and recomputed on-demand from [base], [consume] and [produce] if it is removed by the GC.
 * All mutable operations are not supported. By contract, an iterator returned by [iterator] should be considered immutable.
 */
class LazyCausalNetState(
    private val base: CausalNetState,
    private val consume: Collection<Dependency>,
    private val produce: Collection<Dependency>
) : CausalNetState {

    private var backendImpl = SoftReference<CausalNetStateImpl>(null)

    private val backend: CausalNetStateImpl
        get() {
            var tmp = backendImpl.get()
            if (tmp == null) {
                tmp = doMaterialize(base)
                backendImpl = SoftReference(tmp)
            }
            return tmp
        }

    private fun doMaterialize(base: CausalNetState): CausalNetStateImpl {
        val tmp = CausalNetStateImpl(base)
        for (c in consume)
            tmp.remove(c)
        tmp.addAll(produce)
        return tmp
    }

    override fun hashCode(): Int = backend.hashCode()

    override fun equals(other: Any?): Boolean = Objects.equals(backend, other)

    override fun contains(element: Dependency): Boolean = backend.contains(element)

    override fun addAll(elements: Collection<Dependency>): Boolean = throw NotImplementedError("Not supported")

    override fun clear() = throw NotImplementedError("Not supported")

    override fun removeAll(elements: Collection<Dependency>): Boolean = throw NotImplementedError("Not supported")

    override val isFresh: Boolean
        get() = backend.isFresh
    override val uniqueSize: Int
        get() = backend.uniqueSize

    override fun add(element: Dependency, count: Byte): Boolean = throw NotImplementedError("Not supported")

    override fun add(element: Dependency): Boolean = throw NotImplementedError("Not supported")

    override fun addAll(other: MutableMultiSet<Dependency>): Boolean = throw NotImplementedError("Not supported")

    /**
     * The returned iterator should be considered immutable.
     */
    override fun iterator(): MutableIterator<Dependency> =
        object : MutableIterator<Dependency>, Iterator<Dependency> by backend.uniqueSet().iterator() {
            override fun remove() = throw UnsupportedOperationException()
        }

    override fun uniqueSet(): Set<Dependency> = backend.uniqueSet()

    override fun entrySet(): Set<MutableMultiSet.Bucket<Dependency>> = backend.entrySet()

    override fun countSet(): Collection<Byte> = backend.countSet()

    override fun remove(element: Dependency, count: Byte): Boolean = throw NotImplementedError("Not supported")

    override fun remove(element: Dependency): Boolean = throw NotImplementedError("Not supported")

    override fun isEmpty(): Boolean = backend.isEmpty()

    override fun containsAll(elements: Collection<Dependency>): Boolean = backend.containsAll(elements)

    override fun retainAll(elements: Collection<Dependency>): Boolean = throw NotImplementedError("Not supported")

    override val size: Int
        get() = backend.size

    override fun copy(): ProcessModelState = throw NotImplementedError("Not supported")
}
