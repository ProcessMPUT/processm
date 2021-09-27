package processm.etl.jdbc

import org.testcontainers.containers.Db2Container
import org.testcontainers.images.builder.Transferable
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.MountableFile
import processm.etl.DBMSEnvironmentConfigurator
import processm.etl.Db2Environment
import java.io.File

fun prepare(db2env: Db2Environment, configurator: DBMSEnvironmentConfigurator<Db2Environment, Db2Container>) {
    val tmpfile = File.createTempFile("db2", ".tgz")
    db2env.prepare(configurator, tmpfile.absolutePath)
    println()
    println()
    println()
    println("Now copy ${tmpfile.absolutePath} to the resource ${db2env.persistentDatabaseResourcePath}")
}

class Db2ScriptConfigurator(val schemaScript: String, val insertScript: String) :
    DBMSEnvironmentConfigurator<Db2Environment, Db2Container> {
    override fun beforeStart(environment: Db2Environment, container: Db2Container) {
        container.withInitScript(schemaScript)
    }

    override fun afterStart(environment: Db2Environment, container: Db2Container) {
        JdbcDatabaseDelegate(container, "").execute(
            Db2Environment.groupInserts(insertScript),
            insertScript,
            false,
            true
        )
    }
}

fun prepareSakila() {
    prepare(
        Db2Environment.getSakila(), Db2ScriptConfigurator(
            "sakila/db2-sakila-db/db2-sakila-schema.sql",
            "sakila/db2-sakila-db/db2-sakila-insert-data.sql"
        )
    )
}

fun prepareGSDB() {
    val configurator = object : DBMSEnvironmentConfigurator<Db2Environment, Db2Container> {
        private val containerZipPath = "/tmp/GSDB_DB2_LUW_ZOS_v2r3.zip"
        private lateinit var dbName: String

        override fun beforeStart(environment: Db2Environment, container: Db2Container) {
            dbName = container.databaseName

            container.withCopyFileToContainer(
                MountableFile.forClasspathResource("db2/gsdb/GSDB_DB2_LUW_ZOS_v2r3.zip"),
                containerZipPath
            )
            container.withDatabaseName("") // disable creating DB on start

        }

        override fun afterStart(environment: Db2Environment, container: Db2Container) {
            val scriptPath = "/tmp/gsdb.sh"
            val scriptText = """
#!/bin/sh
. /database/config/${container.username}/sqllib/db2profile
mkdir -p /tmp/gsdb
cd /tmp/gsdb
unzip '$containerZipPath'
cd DB2/unix
sh setupGSDB.sh -createdb -database '$dbName' -noprompt
            """.trimIndent()
            container.copyFileToContainer(Transferable.of(scriptText.toByteArray()), scriptPath)
            with(container.execInContainer("sudo", "-u", container.username, "sh", scriptPath)) {
                println(stdout)
                println()
                System.err.println(stderr)
                println()
                println("Exit code: $exitCode")
            }
        }

    }
    prepare(Db2Environment.getGSDB(), configurator)
}

/**
 * This is a helper program to create and pack a database, which then can be quickly unpacked while bringing up a DB2 container.
 */
fun main() {
//    prepareSakila()
//    prepareGSDB()
}