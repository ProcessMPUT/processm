package processm.core.helpers

import processm.core.logging.logger
import java.lang.reflect.Field
import java.util.*
import kotlin.system.measureTimeMillis
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
    fun subsets() {
        assertEquals(
            setOf(
                setOf(),
                setOf("a"), setOf("b"), setOf("c"),
                setOf("a", "b"), setOf("a", "c"), setOf("c", "b"),
                setOf("a", "b", "c")
            ),
            setOf("a", "b", "c").allSubsets().mapToSet { it.toSet() }
        )
    }

    @Test
    fun subsetsWithoutEmpty() {
        assertEquals(
            setOf(
                setOf("a"), setOf("b"), setOf("c"),
                setOf("a", "b"), setOf("a", "c"), setOf("c", "b"),
                setOf("a", "b", "c")
            ),
            setOf("a", "b", "c").allSubsets(true).mapToSet { it.toSet() }
        )
    }

    @Test
    fun `materialized subsets`() {
        assertEquals(
            setOf(
                setOf(),
                setOf("a"), setOf("b"), setOf("c"),
                setOf("a", "b"), setOf("a", "c"), setOf("c", "b"),
                setOf("a", "b", "c")
            ),
            setOf("a", "b", "c").materializedAllSubsets(false).mapToSet { it.toSet() }
        )
    }

    @Test
    fun `materialized subsets without empty`() {
        assertEquals(
            setOf(
                setOf("a"), setOf("b"), setOf("c"),
                setOf("a", "b"), setOf("a", "c"), setOf("c", "b"),
                setOf("a", "b", "c")
            ),
            setOf("a", "b", "c").materializedAllSubsets(true).mapToSet { it.toSet() }
        )
    }

    @Test
    fun `materializedAllSubsets performance`() {
        val set = "ABCDEFGHIJKLMNOPQRSTU".toHashSet()
        set.materializedAllSubsets(false) // warm up
        measureTimeMillis {
            set.materializedAllSubsets(false)
        }.also { logger().info("Calculated power set with empty subset in $it ms.") }

        measureTimeMillis {
            set.materializedAllSubsets(true)
        }.also { logger().info("Calculated power set without empty subset in $it ms.") }
    }

    @Test
    fun `subsets of empty`() {
        assertEquals(
            setOf(setOf()),
            setOf<Int>().allSubsets().mapToSet { it.toSet() }
        )
    }

    @Test
    fun permutations() {
        assertEquals(
            setOf(
                listOf("a", "b", "c"),
                listOf("a", "c", "b"),
                listOf("b", "a", "c"),
                listOf("b", "c", "a"),
                listOf("c", "a", "b"),
                listOf("c", "b", "a")
            ),
            listOf("a", "b", "c").allPermutations().toSet()
        )
    }
}