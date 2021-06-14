package processm.miners.onlineminer

import org.apache.commons.collections4.MultiSet
import processm.core.models.causalnet.CausalNetState
import processm.core.models.causalnet.CausalNetStateImpl
import processm.core.models.causalnet.Dependency
import processm.core.models.commons.ProcessModelState
import java.lang.ref.SoftReference
import java.util.*

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

    override fun contains(element: Dependency?): Boolean = backend.contains(element)

    override fun addAll(elements: Collection<Dependency>): Boolean = backend.addAll(elements)

    override fun clear() = backend.clear()

    override fun removeAll(elements: Collection<Dependency>): Boolean = backend.removeAll(elements)

    override fun add(element: Dependency?): Boolean = backend.add(element)

    override fun add(`object`: Dependency?, occurrences: Int): Int = backend.add(`object`, occurrences)

    override fun iterator(): MutableIterator<Dependency> = backend.iterator()

    override fun setCount(`object`: Dependency?, count: Int): Int = backend.setCount(`object`, count)

    override fun entrySet(): MutableSet<MultiSet.Entry<Dependency>> = backend.entrySet()

    override fun getCount(`object`: Any?): Int = backend.getCount(`object`)

    override fun uniqueSet(): MutableSet<Dependency> = backend.uniqueSet()

    override fun isEmpty(): Boolean = backend.isEmpty()

    override fun remove(`object`: Any?, occurrences: Int): Int = backend.remove(`object`, occurrences)

    override fun remove(element: Dependency?): Boolean = backend.remove(element)

    override fun containsAll(elements: Collection<Dependency>): Boolean = backend.containsAll(elements)

    override fun retainAll(elements: Collection<Dependency>): Boolean = backend.retainAll(elements)

    override val size: Int
        get() = backend.size

    override fun copy(): ProcessModelState {
        TODO("Not yet implemented")
    }
}