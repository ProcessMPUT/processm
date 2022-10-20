package processm.core.log.attribute

import processm.core.log.AttributeMap
import processm.core.log.MutableAttributeMap
import java.util.*
import kotlin.collections.ArrayList

/**
 * List attribute - composite attribute is an attribute that may contain multiple values
 *
 * The order between the child attributes in this list shall be important.
 *
 * Tag inside XES file: <list>
 */
@Deprecated(message="Getting rid of it", level=DeprecationLevel.ERROR)
class ListAttr(key: String, parentStorage: MutableAttributeMap) :
    Attribute<List<Attribute<*>>>(key, parentStorage) {
    internal val valueInternal: MutableList<Attribute<*>> = ArrayList()
    val value: List<Attribute<*>> = Collections.unmodifiableList(valueInternal)
    override fun getValue(): List<Attribute<*>> = this.value
    override val xesTag: String
        get() = "list"
}
