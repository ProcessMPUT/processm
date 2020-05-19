package processm.core.log.attribute

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Date and time attribute
 *
 * Values for a date and time attribute should be specified in UTC time (see ISO 8601), also known as Zulu time.
 *
 * Tag inside XES file: <date>
 */
class DateTimeAttr(key: String, val value: Instant) : Attribute<Instant>(key) {
    override fun getValue() = this.value
    override val xesTag: String
        get() = "date"

    /**
     * Value to String formatting with ISO 8601
     */
    override fun valueToString(): String {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC).format(this.value)
    }
}