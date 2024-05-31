package processm.miners.causalnet.onlineminer

import com.carrotsearch.hppc.*
import com.carrotsearch.hppc.cursors.ObjectIntCursor
import com.carrotsearch.hppc.predicates.ObjectIntPredicate
import com.carrotsearch.hppc.predicates.ObjectPredicate
import com.carrotsearch.hppc.procedures.ObjectIntProcedure
import processm.core.models.causalnet.CausalNetState
import processm.core.models.causalnet.CausalNetStateImpl
import processm.core.models.causalnet.Dependency
import processm.core.models.commons.ProcessModelState
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
    override fun <T : ObjectIntProcedure<in Dependency>?> forEach(procedure: T): T = backend.forEach(procedure)

    override fun <T : ObjectIntPredicate<in Dependency>?> forEach(predicate: T): T = backend.forEach(predicate)

    override fun equals(other: Any?): Boolean = Objects.equals(backend, other)

    override fun containsKey(element: Dependency?): Boolean = backend.containsKey(element)
    override fun containsAll(other: CausalNetState): Boolean = backend.containsAll(other)

    override fun clear() = throw NotImplementedError("Not supported")
    override fun release() = throw NotImplementedError("Not supported")

    override fun visualizeKeyDistribution(characters: Int): String = throw NotImplementedError("Not supported")

    override val isFresh: Boolean
        get() = backend.isFresh

    /**
     * The returned iterator should be considered immutable.
     */
    override fun iterator(): MutableIterator<ObjectIntCursor<Dependency>> = backend.iterator()

    override fun isNotEmpty(): Boolean = backend.isNotEmpty()

    override fun uniqueSet(): ObjectLookupContainer<Dependency> = backend.uniqueSet()

    override fun isEmpty(): Boolean = backend.isEmpty()
    override fun removeAll(container: ObjectContainer<in Dependency>?): Int = throw NotImplementedError("Not supported")

    override fun removeAll(predicate: ObjectPredicate<in Dependency>?): Int = throw NotImplementedError("Not supported")

    override fun removeAll(predicate: ObjectIntPredicate<in Dependency>?): Int =
        throw NotImplementedError("Not supported")

    override fun keys(): ObjectCollection<Dependency> = backend.keys()

    override fun values(): IntContainer = backend.values()

    override fun get(key: Dependency?): Int = backend.get(key)

    override fun getOrDefault(key: Dependency?, defaultValue: Int): Int = backend.getOrDefault(key, defaultValue)

    override fun put(key: Dependency?, value: Int): Int = throw NotImplementedError("Not supported")

    override fun putAll(container: ObjectIntAssociativeContainer<out Dependency>?): Int =
        throw NotImplementedError("Not supported")

    override fun putAll(iterable: MutableIterable<ObjectIntCursor<out Dependency>>?): Int =
        throw NotImplementedError("Not supported")

    override fun putOrAdd(key: Dependency?, putValue: Int, incrementValue: Int): Int =
        throw NotImplementedError("Not supported")

    override fun addTo(key: Dependency?, additionValue: Int): Int = throw NotImplementedError("Not supported")

    override fun remove(element: Dependency?): Int = throw NotImplementedError("Not supported")
    override fun indexOf(key: Dependency?): Int = backend.indexOf(key)

    override fun indexExists(index: Int): Boolean = backend.indexExists(index)

    override fun indexGet(index: Int): Int = backend.indexGet(index)

    override fun indexReplace(index: Int, newValue: Int): Int = throw NotImplementedError("Not supported")

    override fun indexInsert(index: Int, key: Dependency?, value: Int) = throw NotImplementedError("Not supported")

    override fun indexRemove(index: Int): Int = throw NotImplementedError("Not supported")

    override fun containsAll(elements: Collection<Dependency>): Boolean = backend.containsAll(elements)

    override fun size(): Int = backend.size()

    override fun copy(): ProcessModelState = throw NotImplementedError("Not supported")
}
