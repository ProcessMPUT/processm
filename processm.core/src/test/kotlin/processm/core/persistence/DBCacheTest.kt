package processm.core.persistence

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.junit.jupiter.api.Timeout
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import java.lang.management.ManagementFactory
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

@Timeout(20L, unit = TimeUnit.SECONDS)
class DBCacheTest {
    @Test
    fun jmxTest() {
        // make sure DBConnectionPool is loaded
        DBCache.getMainDBPool().getConnection().close()

        // verify if JMX interface is up and running
        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        val baseName = "processm:name=DBConnectionPool"
        val bean = jmxServer.queryMBeans(null, null).first {
            it.objectName.toString().startsWith(baseName)
        }
        assertNotNull(bean)
    }

    @Test
    fun getConnectionTest() {
        DBCache.getMainDBPool().getConnection().use {
            assertEquals(true, it.isValid(3))
        }
    }

    @Test
    fun getDataStoreTest() {
        DBCache.getMainDBPool().getDataStore().connection.use {
            assertEquals(true, it.isValid(3))
        }
    }

    @Test
    fun databaseTest() {
        transactionMain {
            assertEquals(false, this.db.connector().isClosed)
        }
    }

    /**
     * Tests concurrent access to [DBConnectionPool.database].
     */
    @Test
    fun concurrentDatabaseUseTest() {
        try {
            val barrier = CyclicBarrier(2) {
                println("Barrier released")
            }
            val finishSemaphore = Semaphore(0)
            val finallyExpected = setOf("A", "B", "C", "D")

            transactionMain {
                addLogger(StdOutSqlLogger)
                // PostgreSQL poorly supports concurrent modification of schema, so we create schema in advance.
                SchemaUtils.create(Dummies)
            }

            thread {
                transactionMain {
                    addLogger(StdOutSqlLogger)

                    val A = Dummy.new {
                        value = "A"
                    }

                    val AfromDB = Dummy.find {
                        Dummies.value eq "A"
                    }.first()

                    barrier.await()

                    // verify whether two concurrent transactions that share the same Database are conflicting
                    val B = Dummy.new {
                        value = "B"
                    }

                    val BfromDB = Dummy.find {
                        Dummies.value eq "B"
                    }.first()

                    barrier.await()

                    assertNotEquals(A, B)
                    assertEquals(A, AfromDB)
                    assertEquals(B, BfromDB)
                    assertNotEquals(A, BfromDB)
                    assertNotEquals(B, AfromDB)

                    // verify transaction isolation
                    val CfromDB = Dummy.find {
                        Dummies.value eq "C"
                    }.firstOrNull()

                    val DfromDB = Dummy.find {
                        Dummies.value eq "D"
                    }.firstOrNull()

                    assertEquals(null, CfromDB)
                    assertEquals(null, DfromDB)

                    barrier.await()
                }

                transactionMain {
                    barrier.await()
                    // verify durability
                    val actual = Dummy.all().map { it.value }
                    assertTrue(finallyExpected.containsAll(actual))
                }

                finishSemaphore.release()
            }

            thread {
                transactionMain {
                    addLogger(StdOutSqlLogger)

                    val C = Dummy.new {
                        value = "C"
                    }

                    val CfromDB = Dummy.find {
                        Dummies.value eq "C"
                    }.first()

                    barrier.await()

                    // verify whether two concurrent transactions that share the same Database are conflicting
                    val D = Dummy.new {
                        value = "D"
                    }
                    val DfromDB = Dummy.find {
                        Dummies.value eq "D"
                    }.first()

                    barrier.await()

                    assertNotEquals(C, D)
                    assertEquals(C, CfromDB)
                    assertEquals(D, DfromDB)
                    assertNotEquals(C, DfromDB)
                    assertNotEquals(D, CfromDB)

                    // verify transaction isolation
                    val AfromDB = Dummy.find {
                        Dummies.value eq "A"
                    }.firstOrNull()

                    val BfromDB = Dummy.find {
                        Dummies.value eq "B"
                    }.firstOrNull()

                    assertEquals(null, AfromDB)
                    assertEquals(null, BfromDB)

                    barrier.await()
                }

                transactionMain {
                    barrier.await()
                    // verify durability
                    val actual = Dummy.all().map { it.value }
                    assertTrue(finallyExpected.containsAll(actual))
                }

                finishSemaphore.release()
            }

            finishSemaphore.acquire(2)
        } finally {
            transactionMain {
                SchemaUtils.drop(Dummies)
            }
        }
    }

    object Dummies : IntIdTable() {
        val value = varchar("value", 50)
    }

    class Dummy(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Dummy>(Dummies)

        var value by Dummies.value
    }
}
