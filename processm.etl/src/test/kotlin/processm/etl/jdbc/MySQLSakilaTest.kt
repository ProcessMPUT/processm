package processm.etl.jdbc

import processm.etl.DBMSEnvironment
import processm.etl.MySQLEnvironment
import java.sql.Connection


class MySQLSakilaTest : ContinuousQueryTest() {

    override val sqlUUID = "char"  // MySQL doesn't seem to have a dedicated type for UUIDs
    override val sqlText = "char"
    override val sqlInt = "unsigned"   // MySQL doesn't seem to differentiate between int and long
    override val sqlLong = "unsigned"
    override val columnQuot = '`'

    override val etlConfigurationName: String = "Test ETL process for MySQL Sakila DB"

    override fun initExternalDB(): DBMSEnvironment<*> = MySQLEnvironment.getSakila()

    override fun insertNewRental(
        conn: Connection,
        rental_date: java.sql.Timestamp,
        inventory_id: Int,
        customer_id: Int,
        return_date: java.sql.Timestamp?,
        staff_id: Int
    ): Int {
        val rentalId: Int
        conn.prepareStatement("INSERT INTO rental(rental_date,inventory_id,customer_id,return_date,staff_id) VALUES(?,?,?,?,?)")
            .use { stmt ->
                stmt.setObject(1, rental_date)
                stmt.setObject(2, inventory_id)
                stmt.setObject(3, customer_id)
                stmt.setObject(4, return_date)
                stmt.setObject(5, staff_id)
                stmt.execute()
            }
        conn.createStatement().use { stmt ->
            rentalId = stmt.executeQuery("select last_insert_id()").use {
                it.next()
                it.getInt(1)
            }
        }
        return rentalId
    }
}
