package processm.etl.jdbc

import processm.etl.Db2Environment
import java.io.File

fun main() {
    val tmpfile = File.createTempFile("sakila", ".tgz")
    val db2env = Db2Environment.getSakila()
    db2env.prepare(
        "sakila/db2-sakila-db/db2-sakila-schema.sql",
        "sakila/db2-sakila-db/db2-sakila-insert-data.sql",
        tmpfile.absolutePath
    )
    println()
    println()
    println()
    println("Now copy ${tmpfile.absolutePath} to the resource ${db2env.persistentDatabaseResourcePath}")
}