package processm.core.log.attribute

import java.util.*
import kotlin.collections.ArrayList

class ListAttr(key: String) : Attribute<List<Attribute<*>>>(key) {
    internal val valueInternal: MutableList<Attribute<*>> = ArrayList()
    override fun getValue() = Collections.unmodifiableList(valueInternal)
}
