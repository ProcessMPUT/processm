package processm.etl.jdbc

import oracle.jdbc.OraclePreparedStatement
import processm.etl.DBMSEnvironment
import processm.etl.OracleEnvironment
import java.sql.Connection
import java.sql.Types
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

class OracleSakilaTest : ContinuousQueryTest() {

    override val etlConfiguratioName: String = "Test ETL process for Oracle Sakila DB"

    override val dummyFrom: String = " from dual"
    override val sqlLong = "NUMBER(*, 0)"
    override val sqlText: String = "VARCHAR(1000)"
    override val sqlUUID: String = "VARCHAR(1000)"

    override fun lastEventExternalIdFromNumber(numberOfEvents: Long) = numberOfEvents.toDouble().toString()

    override fun initExternalDB(): DBMSEnvironment<*> = OracleEnvironment.getSakila()


    override fun insertNewRental(
        conn: Connection,
        rental_date: java.sql.Timestamp,
        inventory_id: Int,
        customer_id: Int,
        return_date: java.sql.Timestamp?,
        staff_id: Int
    ): Int {
        val rentalId: Int

        conn.prepareStatement("INSERT INTO rental(rental_date,inventory_id,customer_id,return_date,staff_id) VALUES(?,?,?,?,?) RETURNING rental_id INTO ?")
            .use { stmt ->
                stmt as OraclePreparedStatement
                stmt.setObject(1, rental_date)
                stmt.setObject(2, inventory_id)
                stmt.setObject(3, customer_id)
                stmt.setObject(4, return_date)
                stmt.setObject(5, staff_id)
                stmt.registerReturnParameter(6, Types.INTEGER)
                stmt.execute()
                rentalId = stmt.returnResultSet.use {
                    it.next()
                    it.getInt(1)
                }
            }
        return rentalId
    }

    private lateinit var defaultLocale: Locale

    @BeforeTest
    fun setLocaleToUS() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

}