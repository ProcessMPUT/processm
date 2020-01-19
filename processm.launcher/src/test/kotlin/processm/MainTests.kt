package processm

import org.junit.Test
import java.lang.reflect.Field
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class MainTests {
    private val envProperty = "processm.envProperty"
    private val envPropertyValue = "true"
    private val overriddenProperty = "processm.testMode"
    private val overriddenPropertyValue = "true"

    /**
     * Verifies whether configuration from config.properties takes precedence over environment variables.
     */
    @Test
    fun loadConfigurationTest() {
        assertEquals(null, System.getProperty(envProperty))
        assertEquals(null, System.getProperty(overriddenProperty))

        val env = mapOf(
            "PROCESSM_$envProperty" to envPropertyValue,
            "PROCESSM_$overriddenProperty" to overriddenPropertyValue
        )
        setEnv(env)

        Main.loadConfiguration()
        assertEquals(envPropertyValue, System.getProperty(envProperty))
        assertNotEquals(overriddenPropertyValue, System.getProperty(overriddenProperty))
    }

    /**
     * Method comes from https://stackoverflow.com/a/7201825/1016631
     */
    @Throws(Exception::class)
    private fun setEnv(newenv: Map<String, String>?) {
        try {
            val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
            val theEnvironmentField: Field = processEnvironmentClass.getDeclaredField("theEnvironment")
            theEnvironmentField.isAccessible = true
            val env =
                theEnvironmentField.get(null) as MutableMap<String, String>
            env.putAll(newenv!!)
            val theCaseInsensitiveEnvironmentField: Field =
                processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment")
            theCaseInsensitiveEnvironmentField.isAccessible = true
            val cienv =
                theCaseInsensitiveEnvironmentField.get(null) as MutableMap<String, String>
            cienv.putAll(newenv)
        } catch (e: NoSuchFieldException) {
            val classes = Collections::class.java.declaredClasses
            val env = System.getenv()
            for (cl in classes) {
                if ("java.util.Collections\$UnmodifiableMap" == cl.name) {
                    val field: Field = cl.getDeclaredField("m")
                    field.isAccessible = true
                    val obj: Any = field.get(env)
                    val map =
                        obj as MutableMap<String, String>
                    map.clear()
                    map.putAll(newenv!!)
                }
            }
        }
    }
}