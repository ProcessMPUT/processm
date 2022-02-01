package processm.tools.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

class AbstractConfigurationTest {

    val prefix = "7537bc1f-aa17-43f9-bed9-ad24ef5861dc"

    @Test
    fun readDefault() {
        val cfg = object:AbstractConfiguration() {
            var test:String = "test"
                private set
            init {
                initFromEnvironment("$prefix.readDefault")
            }
        }
        assertEquals("test", cfg.test)
    }

    @Test
    fun readFromProperty() {
        val prefix="$prefix.readFromProperty"
        System.setProperty("$prefix.test", "property")
        val cfg = object:AbstractConfiguration() {
            var test:String = "test"
                private set
            init {
                initFromEnvironment(prefix)
            }
        }
        assertEquals("property", cfg.test)
    }

    @Test
    fun readFromEnv() {
        val prefix="$prefix.readFromEnvironment"
        val env= mapOf("$prefix.test" to "environment")
        val cfg = object:AbstractConfiguration() {
            var test:String = "test"
                private set
            init {
                initFromEnvironment(prefix, env)
            }
        }
        assertEquals("environment", cfg.test)
    }
}