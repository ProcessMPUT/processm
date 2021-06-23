package processm.core.helpers

import org.junit.jupiter.api.Disabled
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
        val validPowerset = setOf(
            setOf(),
            setOf("a"), setOf("b"), setOf("c"),
            setOf("a", "b"), setOf("a", "c"), setOf("c", "b"),
            setOf("a", "b", "c")
        )
        val calculatedPowerset = setOf("a", "b", "c").allSubsets()

        // test PowerSetImpl<T>.equals()
        assertEquals(validPowerset, calculatedPowerset)
        assertEquals(calculatedPowerset, validPowerset)
        assertEquals(calculatedPowerset, calculatedPowerset)

        // test PowerSetImpl<T>.hashCode()
        assertEquals(validPowerset.hashCode(), calculatedPowerset.hashCode())
        assertEquals(calculatedPowerset.hashCode(), calculatedPowerset.hashCode())


        // test PowerSetImpl<T>.contains()
        assertTrue(emptySet() in calculatedPowerset)
        assertFalse(setOf("a", "b", "c", "d") in calculatedPowerset)
        for (validSubset in validPowerset) {
            // test PowerSetImpl<T>.contains()
            assertTrue(validSubset in calculatedPowerset)

            // test Subset<T>.contains() and Subset<T>.containsAll():
            assertTrue(calculatedPowerset.any { calculatedSubset -> calculatedSubset.containsAll(validSubset) })

            // test Subset<T>.indexOf()
            val index = calculatedPowerset.indexOf(validSubset)
            assertTrue(index >= 0)
            assertEquals(index, calculatedPowerset.lastIndexOf(validSubset))

            // test Subset<T>.equals()
            val calculatedSubset = calculatedPowerset[index]
            assertEquals(validSubset, calculatedSubset)
            assertEquals(calculatedSubset, calculatedSubset)

            // test Subset<T>.hashCode()
            assertEquals(validSubset.hashCode(), calculatedSubset.hashCode())
            assertEquals(calculatedSubset.hashCode(), calculatedSubset.hashCode())
        }
    }

    @Test
    fun subsetsWithoutEmpty() {
        val validPowerset = setOf(
            setOf("a"), setOf("b"), setOf("c"),
            setOf("a", "b"), setOf("a", "c"), setOf("c", "b"),
            setOf("a", "b", "c")
        )
        val calculatedPowerset = setOf("a", "b", "c").allSubsets(true)

        // test PowerSetImpl<T>.equals()
        assertEquals(validPowerset, calculatedPowerset)
        assertEquals(calculatedPowerset, validPowerset)
        assertEquals(calculatedPowerset, calculatedPowerset)

        // test PowerSetImpl<T>.hashCode()
        assertEquals(validPowerset.hashCode(), calculatedPowerset.hashCode())
        assertEquals(calculatedPowerset.hashCode(), calculatedPowerset.hashCode())


        // test PowerSetImpl<T>.contains()
        assertFalse(emptySet() in calculatedPowerset)
        assertFalse(setOf("a", "b", "c", "d") in calculatedPowerset)
        for (validSubset in validPowerset) {
            // test PowerSetImpl<T>.contains()
            assertTrue(validSubset in calculatedPowerset)

            // test Subset<T>.contains() and Subset<T>.containsAll():
            assertTrue(calculatedPowerset.any { calculatedSubset -> calculatedSubset.containsAll(validSubset) })

            // test Subset<T>.indexOf()
            val index = calculatedPowerset.indexOf(validSubset)
            assertTrue(index >= 0)
            assertEquals(index, calculatedPowerset.lastIndexOf(validSubset))

            // test Subset<T>.equals()
            val calculatedSubset = calculatedPowerset[index]
            assertEquals(validSubset, calculatedSubset)
            assertEquals(calculatedSubset, calculatedSubset)

            // test Subset<T>.hashCode()
            assertEquals(validSubset.hashCode(), calculatedSubset.hashCode())
            assertEquals(calculatedSubset.hashCode(), calculatedSubset.hashCode())
        }
    }

    @Disabled("Intended for manual execution")
    @Test
    fun `allSubsets performance`() {
        val list = "ABCDEFGHIJKLMNOPQRSTUWVXYZ".toList()
        // warm up
        for (subset in list.allSubsets(false)) {
            for (item in subset) {
                // nothing
            }
        }
        measureTimeMillis {
            for (subset in list.allSubsets(false)) {
                for (item in subset) {
                    // nothing
                }
            }
        }.also { logger().info("Calculated power set with empty subset in $it ms.") }

        measureTimeMillis {
            for (subset in list.allSubsets(true)) {
                for (item in subset) {
                    // nothing
                }
            }
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
    fun `subsets of empty filtered for non-empty`() {
        assertEquals(
            setOf(),
            setOf<Int>().allSubsets(true).mapToSet { it.toSet() }
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

    @ExperimentalStdlibApi
    @Test
    fun `all subsets up to size 3`() {
        val subsets = listOf("a", "b", "c", "d").allSubsetsUpToSize(3).toSet()
        assertEquals(
            setOf(
                setOf("a"),
                setOf("b"),
                setOf("c"),
                setOf("d"),
                setOf("a", "b"),
                setOf("a", "c"),
                setOf("a", "d"),
                setOf("b", "c"),
                setOf("b", "d"),
                setOf("c", "d"),
                setOf("a", "b", "c"),
                setOf("a", "b", "d"),
                setOf("a", "c", "d"),
                setOf("b", "c", "d")
            ), subsets
        )
    }

    @ExperimentalStdlibApi
    @Test
    fun `all subsets up to size 2`() {
        val subsets = listOf("a", "b", "c", "d").allSubsetsUpToSize(2).toSet()
        assertEquals(
            setOf(
                setOf("a"),
                setOf("b"),
                setOf("c"),
                setOf("d"),
                setOf("a", "b"),
                setOf("a", "c"),
                setOf("a", "d"),
                setOf("b", "c"),
                setOf("b", "d"),
                setOf("c", "d")
            ), subsets
        )
    }

    @ExperimentalStdlibApi
    @Test
    fun `all subsets up to size 1`() {
        val subsets = listOf("a", "b", "c", "d").allSubsetsUpToSize(1).toSet()
        assertEquals(
            setOf(
                setOf("a"),
                setOf("b"),
                setOf("c"),
                setOf("d")
            ), subsets
        )
    }

    @ExperimentalStdlibApi
    @Test
    fun `all subsets up to size 4`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(list.allSubsets(true), list.allSubsetsUpToSize(4).toSet())
    }

    private fun testCount(n:Int) {
        val input = (0 until n).toList()
        assertEquals(n, input.size)
        val expected = 0 + //ignored empty subsets
                n +   // subsets of size 1
                n * (n - 1) / 2 //subsets of size 2
        assertEquals(expected, input.allSubsetsUpToSize(2).count())
    }

    @Test
    fun `count subsets up to size 2 of list of 40`() {
        testCount(40)
    }

    @Test
    fun `count subsets up to size 2 of list of 70`() {
        testCount(70)
    }

    @Test
    fun `count subsets up to size 2 of list of 300`() {
        testCount(300)
    }
}