package processm.etl

import org.testcontainers.containers.MySQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.MountableFile
import processm.core.logging.logger
import java.io.File


class MySQLScriptConfigurator(val scripts: Iterable<String>, val auxiliaries: Iterable<String>) :
    DBMSEnvironmentConfigurator<MySQLEnvironment, MySQLContainer<*>> {
    companion object {
        private val logger = logger()
        private const val containerShellScript = "/tmp/script.sh"
    }

    override fun beforeStart(environment: MySQLEnvironment, container: MySQLContainer<*>) {
        // Sakila database turned out to be incompatible with Container.withInitScript due to the DELIMITER keyword
        // I believe this to be a wider problem of mysqldump's output being compatible with the mysql command-line client, but not necessarily with APIs
        // The insert script is, on the other hand, simply too big for JDBC
        // SETs were copied from https://dba.stackexchange.com/questions/44297/speeding-up-mysqldump-reload/44309#44309 and they seem to vastly improve performance
        val scriptText = StringBuilder()
        scriptText.append(
            """#!/bin/sh
                cd /tmp
            (echo 'SET autocommit=0; SET unique_checks=0; SET foreign_key_checks=0;'"""
        )
        for ((idx, script) in scripts.withIndex()) {
            val containerPath = "/tmp/script$idx.sql"
            container.withCopyFileToContainer(MountableFile.forClasspathResource(script), containerPath)
            scriptText.append("; cat '$containerPath' ")
        }
        for (source in auxiliaries)
            container.withCopyFileToContainer(MountableFile.forClasspathResource(source), "/tmp/" + File(source).name)
        scriptText.append("; echo 'commit')|mysql --user='root' --password='${environment.password}' '${environment.dbName}'")
        val script = File.createTempFile("MySQLScriptConfigurator", null)
        script.writeText(scriptText.toString())
        container.withCopyFileToContainer(MountableFile.forHostPath(script.toPath()), containerShellScript)
    }

    override fun afterStart(environment: MySQLEnvironment, container: MySQLContainer<*>) {
        with(container.execInContainer("sh", containerShellScript)) {
            logger.debug(stdout)
            logger.warn(stderr)
            check(exitCode == 0)
        }
    }
}

class MySQLEnvironment(
    dbName: String,
    user: String,
    password: String,
    val configurator: DBMSEnvironmentConfigurator<MySQLEnvironment, MySQLContainer<*>>
) : DBMSEnvironment<MySQLContainer<*>>(
    dbName,
    user,
    password
) {
    companion object {
        fun getSakila(): MySQLEnvironment =
            MySQLEnvironment(
                "sakila",
                "postgres",
                "sakila_password",
                MySQLScriptConfigurator(
                    listOf(
                        "sakila/mysql-sakila-db/mysql-sakila-schema.sql",
                        "sakila/mysql-sakila-db/mysql-sakila-insert-data.sql"
                    ),
                    emptyList()
                )
            )

        fun getEmployees(): MySQLEnvironment =
            MySQLEnvironment(
                "employees", "employees", "password", MySQLScriptConfigurator(
                    listOf("mysql/test_db/employees.sql"),
                    listOf(
                        "mysql/test_db/load_departments.dump",
                        "mysql/test_db/load_dept_emp.dump",
                        "mysql/test_db/load_dept_manager.dump",
                        "mysql/test_db/load_employees.dump",
                        "mysql/test_db/load_salaries1.dump",
                        "mysql/test_db/load_salaries2.dump",
                        "mysql/test_db/load_salaries3.dump",
                        "mysql/test_db/load_titles.dump",
                        "mysql/test_db/show_elapsed.sql"
                    )
                )
            )
    }

    override fun initAndRun(): MySQLContainer<*> {
        val container = initContainer()
            .withDatabaseName(dbName)
            .withUsername(user)
            .withPassword(password)
        configurator.beforeStart(this, container)
        Startables.deepStart(listOf(container)).join()
        configurator.afterStart(this, container)
        return container as MySQLContainer
    }

    override fun initContainer(): MySQLContainer<*> {
        return MySQLContainer<MySQLContainer<*>>("mysql:8.0.26")
    }
}
