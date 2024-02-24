package processm.core.log

import processm.core.log.attribute.AttributeMap
import processm.helpers.toUUID
import java.sql.ResultSet
import java.util.*

private val gmtCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))

/**
 * Reads the value contained in a record from `event_attributes` table in the database.
 * This used to be a private function of `DBHierarchicalXESInputStream`, but was exposed for the ETL processes to access it.
 * Use with caution!
 */
fun attributeFromRecord(record: ResultSet): Any {
    with(record) {
        val type = getString("type")!!
        assert(type.length >= 2)
        return when (type[0]) {
            's' -> getString("string_value")
            'f' -> getDouble("real_value")
            'i' -> when (type[1]) {
                'n' -> getLong("int_value")
                'd' -> getString("uuid_value").toUUID()!!
                else -> throw IllegalStateException("Invalid attribute type ${getString("type")} in the database.")
            }

            'd' -> getTimestamp("date_value", gmtCalendar).toInstant()
            'b' -> getBoolean("bool_value")
            'l' -> AttributeMap.LIST_TAG
            else -> throw IllegalStateException("Invalid attribute type ${getString("type")} in the database.")
        }
    }
}
