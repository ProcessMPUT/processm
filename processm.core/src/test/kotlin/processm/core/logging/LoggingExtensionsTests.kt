package processm.core.logging

import kotlin.reflect.full.companionObject
import kotlin.test.Test
import kotlin.test.assertEquals

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
}