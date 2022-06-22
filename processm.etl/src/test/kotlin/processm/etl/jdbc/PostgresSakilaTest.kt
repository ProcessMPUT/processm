package processm.etl.jdbc

import processm.etl.DBMSEnvironment
import processm.etl.PostgreSQLEnvironment

class PostgresSakilaTest : ContinuousQueryTest() {

    override val expectedNumberOfEvents = 47954L
    override val expectedNumberOfTracesInTheFirstBatch = 1804L

    override val etlConfigurationName: String = "Test ETL process for PostgreSQL Sakila DB"

    override fun initExternalDB(): DBMSEnvironment<*> = PostgreSQLEnvironment.getSakila()

}