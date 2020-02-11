package processm.core.log.attribute

import java.util.*

/**
 * Date and time attribute
 *
 * Values for a date and time attribute should be specified in UTC time (see ISO 86016), also known as Zulu time.
 *
 * Tag inside XES file: <date>
 */
class DateTimeAttr(key: String, val value: Date) : Attribute<Date>(key) {
    override fun getValue() = this.value
    override val xesTag: String
        get() = "date"
}