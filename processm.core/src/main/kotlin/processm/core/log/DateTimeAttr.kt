package processm.core.log

import java.util.Date

class DateTimeAttr(key: String, val value: Date) {
    val key: String = key.intern()
}
