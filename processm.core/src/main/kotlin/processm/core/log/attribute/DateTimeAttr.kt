package processm.core.log.attribute

import processm.core.log.AttributeMap
import processm.core.log.MutableAttributeMap
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
@Deprecated(message="Getting rid of it", level=DeprecationLevel.ERROR)
class DateTimeAttr(key: String, val value: Instant, parentStorage: MutableAttributeMap) :
    Attribute<Instant>(key, parentStorage) {
    override fun getValue(): Instant = this.value
    override val xesTag: String
        get() = "date"

    /**
     * Value to String formatting with ISO 8601
     */
    override fun valueToString(): String {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC).format(this.value)
    }
}