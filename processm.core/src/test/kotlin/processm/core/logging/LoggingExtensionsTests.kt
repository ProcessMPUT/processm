package processm.core.logging

import kotlin.reflect.full.companionObject
import ch.qos.logback.classic.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggingExtensionsTests {
    companion object{
        fun getLoggerCompanionObjectTest() {
            val logger = logger()
            assertEquals(LoggingExtensionsTests::class.qualifiedName, logger.name)
        }
    }

    @Test
    fun getLoggerTest() {
        val logger = logger()
        assertEquals(LoggingExtensionsTests::class.qualifiedName, logger.name)

        getLoggerCompanionObjectTest()
    }

    @Test
    fun lazyTraceTest() {
        var called = false
        val logger = logger() as ch.qos.logback.classic.Logger
        val oldLevel = logger.level
        logger.level = Level.DEBUG
        logger.trace {
            called = true
            "message"
        }
        assertFalse(called)
        logger.level = Level.TRACE
        logger().trace {
            called = true
            "message"
        }
        assertTrue(called)
        logger.level = oldLevel
    }
}