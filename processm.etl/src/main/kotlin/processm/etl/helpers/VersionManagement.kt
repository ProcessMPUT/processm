package processm.etl.helpers

import java.sql.Connection

fun Connection.nextVersion() = prepareStatement("select nextval('xes_version');").use {
    it.executeQuery().use { rs ->
        check(rs.next())
        rs.getLong(1)
    }
}