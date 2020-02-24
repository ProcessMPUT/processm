package processm.core.log.attribute

import java.util.*
import kotlin.collections.ArrayList

/**
 * List attribute - composite attribute is an attribute that may contain multiple values
 *
 * The order between the child attributes in this list shall be important.
 *
 * Tag inside XES file: <list>
 */
class ListAttr(key: String) : Attribute<List<Attribute<*>>>(key) {
    internal val valueInternal: MutableList<Attribute<*>> = ArrayList()
    val value: List<Attribute<*>> = Collections.unmodifiableList(valueInternal)
    override fun getValue() = this.value
    override val xesTag: String
        get() = "list"
}
