package processm.core.log

import java.sql.Connection

/**
 * Returns the next version number from the DB sequence `xes_version`
 */
fun Connection.nextVersion(): Long = prepareStatement("select nextval('xes_version');").use {
    it.executeQuery().use { rs ->
        check(rs.next())
        rs.getLong(1)
    }
}