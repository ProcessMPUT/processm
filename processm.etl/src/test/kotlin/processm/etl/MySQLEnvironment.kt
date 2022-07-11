package processm.etl

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.images.builder.Transferable
import org.testcontainers.lifecycle.Startables
import processm.core.logging.logger
import processm.etl.DBMSEnvironment.Companion.TEST_DATABASES_PATH
import java.io.File
import java.sql.Connection

class MySQLEnvironment(
    val container: MySQLContainer<*>,
    val dbName: String
) : DBMSEnvironment<MySQLContainer<*>> {
    companion object {

        private val logger = logger()

        private const val user = "root"
        private const val password = "sakila_password"

        fun createContainer(): MySQLContainer<*> = MySQLContainer("mysql:8.0.26")

        private val sharedContainerDelegate = lazy {
            val container = createContainer()
                .withUsername(user)
                .withPassword(password)
                .withFileSystemBind(TEST_DATABASES_PATH.absolutePath, "/tmp/test-databases/", BindMode.READ_ONLY)
            Startables.deepStart(listOf(container)).join()
            return@lazy container
        }
        private val sharedContainer by sharedContainerDelegate

        private val sakilaEnv by lazy {
            val env = MySQLEnvironment(sharedContainer, "sakila")
            env.configure(
                listOf(
                    "sakila/mysql-sakila-db/mysql-sakila-schema.sql",
                    "sakila/mysql-sakila-db/mysql-sakila-insert-data.sql"
                ),
                emptyList()
            )
            return@lazy env
        }

        fun getSakila(): MySQLEnvironment = sakilaEnv

        private val employeesEnv by lazy {
            val env = MySQLEnvironment(sharedContainer, "employees")
            env.configure(
                listOf("test_db/employees.sql"),
                listOf(
                    "test_db/load_departments.dump",
                    "test_db/load_dept_emp.dump",
                    "test_db/load_dept_manager.dump",
                    "test_db/load_employees.dump",
                    "test_db/load_salaries1.dump",
                    "test_db/load_salaries2.dump",
                    "test_db/load_salaries3.dump",
                    "test_db/load_titles.dump",
                    "test_db/show_elapsed.sql"
                )
            )
            return@lazy env
        }

        fun getEmployees(): MySQLEnvironment = employeesEnv
    }

    fun configure(scripts: Iterable<String>, auxiliaries: Iterable<String>) {
        val containerShellScript = "/tmp/script.sh"

        // Sakila database turned out to be incompatible with Container.withInitScript due to the DELIMITER keyword
        // I believe this to be a wider problem of mysqldump's output being compatible with the mysql command-line client, but not necessarily with APIs
        // The insert script is, on the other hand, simply too big for JDBC
        // SETs were copied from https://dba.stackexchange.com/questions/44297/speeding-up-mysqldump-reload/44309#44309 and they seem to vastly improve performance
        val scriptText = StringBuilder()
        // FIXME: it sets the working directory to the path where the first script resides; may fail when files reside in different directories
        scriptText.append(
            """#!/bin/sh                 
                cd /tmp/test-databases/${File(scripts.first()).parent}
            (echo 'create database $dbName; use $dbName;'            
            echo 'SET autocommit=0; SET unique_checks=0; SET foreign_key_checks=0;'"""
        )
        for (script in scripts) {
            val containerPath = "/tmp/test-databases/$script"
            scriptText.append("; cat '$containerPath' ")
        }

        scriptText.append("; echo 'commit')|mysql --user='root' --password='$password'")
        container.copyFileToContainer(Transferable.of(scriptText.toString().toByteArray()), containerShellScript)

        with(container.execInContainer("sh", containerShellScript)) {
            logger.debug(stdout)
            logger.warn(stderr)
            check(exitCode == 0)
        }

    }

    override val user: String
        get() = container.username
    override val password: String
        get() = container.password
    override val jdbcUrl: String
        get() = container.withDatabaseName(dbName).jdbcUrl

    override fun connect(): Connection = container.withDatabaseName(dbName).createConnection("")

    override fun close() {
        if (!sharedContainerDelegate.isInitialized() || container !== sharedContainer)
            container.close() // otherwise it is testcontainer's responsibility to shutdown the container
    }
}
