package processm.core.log.attribute

import processm.core.log.attribute.AttributeMap
import java.util.*

class UnmodifiableAttributeMap(private val base: AttributeMap) : AttributeMap by base {

    override fun children(key: CharSequence): AttributeMap = UnmodifiableAttributeMap(base.children(key))

    override val entries: Set<Map.Entry<CharSequence, Any?>>
        get() = Collections.unmodifiableSet(base.entries)
    override val keys: Set<CharSequence>
        get() = Collections.unmodifiableSet(base.keys)
    override val values: Collection<Any?>
        get() = Collections.unmodifiableCollection(base.values)

    override fun equals(other: Any?): Boolean = this === other || base == other
}

fun AttributeMap.unmodifiableView() = if (this is UnmodifiableAttributeMap) this else UnmodifiableAttributeMap(this)