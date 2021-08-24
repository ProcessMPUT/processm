package processm.etl

import org.testcontainers.containers.MySQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.MountableFile
import java.io.File

class MySQLEnvironment(
    dbName: String,
    user: String,
    password: String,
    schemaScript: String,
    insertScript: String
) : DBMSEnvironment<MySQLContainer<*>>(
    dbName,
    user,
    password,
    schemaScript,
    insertScript
) {
    companion object {
        fun getSakila(): MySQLEnvironment =
            MySQLEnvironment(
                "sakila",
                "postgres",
                "sakila_password",
                "sakila/mysql-sakila-db/mysql-sakila-schema.sql",
                "sakila/mysql-sakila-db/mysql-sakila-insert-data.sql"
            )
    }

    override fun initAndRun(): MySQLContainer<*> {
        val container = initContainer()
            .withDatabaseName(dbName)
            .withUsername(user)
            .withPassword(password)
        Startables.deepStart(listOf(container)).join()

        // Sakila database turned out to be incompatible with Container.withInitScript due to the DELIMITER keyword
        // I believe this to be a wider problem of mysqldump's output being compatible with the mysql command-line client, but not necessarily with APIs
        // The insert script is, on the other hand, simply too big for JDBC
        // SETs were copied from https://dba.stackexchange.com/questions/44297/speeding-up-mysqldump-reload/44309#44309 and they seem to vastly improve performance
        val tempdir = with(container.execInContainer("mktemp", "-d")) {
            check(this.exitCode == 0)
            return@with stdout.trim()
        }
        val containerSchemaScript = "$tempdir/schema.sql"
        val containerInsertScript = "$tempdir/insert.sql"
        val containerShellScript = "$tempdir/script.sh"
        val script = File.createTempFile("MySQLEnvironment", null)
        script.writeText(
            """#!/bin/sh
            (echo 'SET autocommit=0; SET unique_checks=0; SET foreign_key_checks=0;'; cat '$containerSchemaScript'; cat '$containerInsertScript' )|mysql --user='root' --password='$password' '$dbName' 
        """.trimIndent()
        )
        container.copyFileToContainer(MountableFile.forClasspathResource(schemaScript), containerSchemaScript)
        container.copyFileToContainer(MountableFile.forClasspathResource(insertScript), containerInsertScript)
        container.copyFileToContainer(MountableFile.forHostPath(script.toPath()), containerShellScript)
        with(container.execInContainer("sh", containerShellScript)) {
            println(stdout)
            System.err.println(stderr)
            check(exitCode == 0)
        }
        return container as MySQLContainer
    }

    override fun initContainer(): MySQLContainer<*> {
        return MySQLContainer<MySQLContainer<*>>("mysql:8.0.26")
    }
}
