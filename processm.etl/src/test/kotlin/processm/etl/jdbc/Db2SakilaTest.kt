package processm.etl.jdbc

import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import processm.etl.DBMSEnvironment
import processm.etl.Db2Environment

@DisabledOnOs(OS.WINDOWS) // 2-3 times slower on Windows than on linux and mac
class Db2SakilaTest : ContinuousQueryTest() {

    override val etlConfigurationName: String = "Test ETL process for Db2 Sakila DB"

    override val dummyFrom: String = " FROM SYSIBM.SYSDUMMY1"
    override val sqlText: String = "VARCHAR"
    override val sqlUUID: String = "VARCHAR"

    override fun initExternalDB(): DBMSEnvironment<*> = Db2Environment.getSakila()

    override val insertNewRentalQuery: String
        get() = "SELECT rental_id FROM FINAL TABLE (INSERT INTO rental(rental_date,inventory_id,customer_id,return_date,staff_id) VALUES(?,?,?,?,?))"
}
