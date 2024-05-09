package processm.helpers.time

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField

/**
 * Parses a timestamp with timezone in the ISO-8601 format into [Instant].
 * @throws java.time.format.DateTimeParseException if unable to parse the requested string
 */
inline fun String.parseISO8601(): Instant = DateTimeFormatter.ISO_DATE_TIME.parse(this, Instant::from)

/**
 * Parses a timestamp with timezone in the ISO-8601 format into [Instant].
 * This function trades some safety-checks for performance. E.g.,
 * * The exception messages may be less detailed than these thrown by [parseISO8601] but the normal results of both
 * methods should equal.
 * @throws java.time.format.DateTimeParseException if unable to parse the requested string
 * @throws java.time.DateTimeException if the date/time cannot be represented using [Instant]
 */
inline fun String.fastParseISO8601(): Instant =
    DateTimeFormatter.ISO_DATE_TIME.parse(this) { temporal ->
        val instantSecs = temporal.getLong(ChronoField.INSTANT_SECONDS)
        val nanoOfSecond = temporal.get(ChronoField.NANO_OF_SECOND).toLong()
        Instant.ofEpochSecond(instantSecs, nanoOfSecond)
    }

inline fun Instant.toDateTime(): OffsetDateTime = this.atOffset(ZoneOffset.UTC)

/**
 * Converts an [Instant] to [LocalDateTime] in a uniform way.
 */
fun Instant.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.of("Z"))

/**
 * Calculates the arithmetic mean of the given [timestamp]s with millisecond precision.
 */
fun meanOf(vararg timestamp: Instant): Instant = Instant.ofEpochMilli(
    timestamp.fold(0.0) { acc, instant ->
        acc + instant.toEpochMilli().toDouble() / timestamp.size
    }.toLong()
)

