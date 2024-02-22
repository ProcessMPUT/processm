package processm.helpers

import java.lang.reflect.Field
import java.util.*
import kotlin.test.*

class HelpersTests {
    private val envProperty = "processm.envProperty"
    private val envPropertyValue = "true"
    private val overriddenProperty = "processm.testMode"
    private val overriddenPropertyValue = "true"
    private lateinit var previousEnv: Map<String, String>

    @BeforeTest
    fun setUp() {
        System.clearProperty(envProperty)
        System.clearProperty(overriddenProperty)
        previousEnv = HashMap(System.getenv())
    }

    @AfterTest
    fun cleanUp() {
        System.clearProperty(envProperty)
        System.clearProperty(overriddenProperty)
        setEnv(previousEnv)
        loadConfiguration(true)
    }

    /**
     * Verifies whether configuration from config.properties takes precedence over environment variables.
     */
    @Test
    fun loadConfigurationTest() {
        assertEquals(null, System.getProperty(envProperty))
        assertEquals(null, System.getProperty(overriddenProperty))

        val env = mapOf(
            envProperty.replace(".", "_") to envPropertyValue,
            overriddenProperty.replace(".", "_") to overriddenPropertyValue
        )
        setEnv(env)

        loadConfiguration(true)
        assertEquals(envPropertyValue, System.getProperty(envProperty))
        assertNotEquals(overriddenPropertyValue, System.getProperty(overriddenProperty))
    }

    /**
     * Method comes from https://stackoverflow.com/a/7201825/1016631
     */
    @Suppress("UNCHECKED_CAST")
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

    @Test
    fun `intersect - empty`() {
        assertTrue { intersect(emptyList<Set<Int>>()).isEmpty() }
    }

    @Test
    fun `intersect - one set`() {
        val set = setOf(1, 2, 3)
        assertSame(set, intersect(listOf(set)))
    }

    @Test
    fun `intersect - three sets with common elements`() {
        val set1 = setOf(1, 2, 3)
        val set2 = setOf(1, 2, 4)
        val set3 = setOf(2, 3, 4)
        assertEquals(setOf(2), intersect(listOf(set1, set2, set3)))
    }

    @Test
    fun `intersect - three disjoint sets`() {
        val set1 = setOf(1, 2, 3)
        val set2 = setOf(2, 3, 4)
        val set3 = setOf(4, 5, 6)
        assertEquals(emptySet(), intersect(listOf(set1, set2, set3)))
    }
}
