package processm.etl.jdbc

import processm.etl.DBMSEnvironment
import processm.etl.MSSQLEnvironment

class MSSQLSakilaTest : ContinuousQueryTest() {

    override val etlConfiguratioName: String = "Test ETL process for MS SQL Server Sakila DB"

    override val sqlUUID = "uniqueidentifier"

    override val insertNewRentalQuery =
        "INSERT INTO rental(rental_date,inventory_id,customer_id,return_date,staff_id) OUTPUT INSERTED.rental_id VALUES(?,?,?,?,?)"

    override fun initExternalDB(): DBMSEnvironment<*> = MSSQLEnvironment.getSakila()

}