package processm.core.log

import processm.core.log.attribute.AttributeMap
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

val Any?.xesTag
    get():String = when (this) {
        is Boolean -> "boolean"
        is Instant -> "date"
        is UUID -> "id"
        is Long -> "int"
        is List<*> -> "list"
        is Double -> "float"
        is String -> "string"
        null -> "string"
        else -> throw IllegalArgumentException("Unsupported class: ${this::class}")
    }

fun Any?.valueToString(): String = when (this) {
    is Instant -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC).format(this)
    is List<*> -> throw IllegalArgumentException("Cannot convert list to string")
    else -> this.toString()
}

fun Any?.isAllowedAttributeValue(): Boolean =
    this == null || this is String || this is Long || this is Double || this is Instant || this is UUID || this is Boolean || (this is List<*> && (this as List<Any?>).all { it is AttributeMap })