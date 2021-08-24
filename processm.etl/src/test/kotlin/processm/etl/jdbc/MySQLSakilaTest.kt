package processm.etl.jdbc

import processm.etl.DBMSEnvironment
import processm.etl.MySQLEnvironment
import java.sql.Connection


class MySQLSakilaTest : ContinuousQueryTest() {

    override val sqlUUID = "char"  // MySQL doesn't seem to have a dedicated type for UUIDs
    override val sqlText = "char"
    override val sqlInt = "unsigned"   // MySQL doesn't seem to differentiate between int and long
    override val sqlLong = "unsigned"

    override val getEventSQL
        get() = """
            SELECT * FROM (
SELECT *, row_number() OVER (ORDER BY `time:timestamp`, `concept:instance`) AS event_id FROM (
        SELECT 
            'rent' AS `concept:name`,
            'start' AS `lifecycle:transition`,
            rental_id AS `concept:instance`,
            rental_date AS `time:timestamp`,
            inventory_id AS trace_id
        FROM rental
        WHERE rental_date IS NOT NULL
    UNION ALL
        SELECT 
            'rent' AS `concept:name`,
            'complete' AS `lifecycle:transition`,
            rental_id AS `concept:instance`,
            return_date AS `time:timestamp`,
            inventory_id AS trace_id
        FROM rental
        WHERE return_date IS NOT NULL
    UNION ALL
        SELECT
            'pay' AS `concept:name`,
            'complete' AS `lifecycle:transition`,
            payment_id AS `concept:instance`,
            payment_date AS `time:timestamp`,
            inventory_id AS trace_id
        FROM payment p JOIN rental r ON r.rental_id=p.rental_id
        WHERE payment_date IS NOT NULL    
) sub ) core
WHERE event_id > CAST(? AS $sqlLong)
ORDER BY event_id
    """.trimIndent()

    override val etlConfiguratioName: String = "Test ETL process for MySQL Sakila DB"

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