package processm.core.log.attribute

import java.util.*

class DateTimeAttr(key: String, val value: Date) : Attribute<Date>(key) {
    override fun getValue() = this.value
}