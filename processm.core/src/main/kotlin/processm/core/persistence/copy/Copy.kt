package processm.core.persistence.copy

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * A wrapper around Postgres-specific COPY FROM STDIN operation. It seems that COPY is much faster than a convoluted
 * INSERT with some values inlined. The exposed methods are tailored to the datatypes used in [processm.core.log.attribute.AttributeMap].
 *
 * @param destination The part of the COPY query to insert between COPY and FROM, as per PostgreSQL's documentation.
 * @param extraColumnValues A collection of constant values to append to each inserted row. Can be updated later by calling [setExtraColumnValues]
 */
abstract class Copy(destination: String, extraColumnValues: Collection<String>) {
    companion object {
        const val NULL = "\\N"
        const val DELIMITER = '\t'
        val ISO8601 = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)
    }

    protected val sql = "COPY $destination FROM STDIN WITH (NULL '$NULL', DELIMITER '$DELIMITER')"

    protected lateinit var suffix: String

    init {
        setExtraColumnValues(extraColumnValues)
    }

    fun setExtraColumnValues(extraColumnValues: Collection<String>) {
        suffix = if (extraColumnValues.isNotEmpty()) {
            with(StringBuilder()) {
                extraColumnValues.forEach {
                    append(DELIMITER)
                    append(escape(it) ?: NULL)
                }
                toString()
            }
        } else ""
    }

    protected abstract fun addInternal(text: String?)

    //While this seems expensive, all my tries on making it more efficient by considering multiple characters at once and using StringBuilder failed
    //I hypothesise that most of these characters don't occur in most of the strings, so replace can short-circuit and return the same string
    protected fun escape(value: String?) = value
        ?.replace("\\", "\\\\")
        ?.replace("\b", "\\b")
        ?.replace("\u000c", "\\f")
        ?.replace("\n", "\\n")
        ?.replace("\r", "\\r")
        ?.replace("\t", "\\t")
        ?.replace("\u000b", "\\v")

    fun add(value: String?) {
        addInternal(escape(value))
    }

    //I think none of the remaining datatypes requires escaping

    fun add(value: UUID?) {
        addInternal(value?.toString())
    }

    fun add(value: Instant?) {
        if (value !== null)
            addInternal(ISO8601.format(value))
        else
            addInternal(null as String?)
    }

    fun add(value: Long?) {
        addInternal(value?.toString())
    }

    fun add(value: Boolean?) {
        addInternal(value?.toString())
    }

    fun add(value: Double?) {
        addInternal(value?.toString())
    }
}