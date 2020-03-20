package processm.core.log

import io.mockk.Called
import io.mockk.every
import io.mockk.spyk
import io.mockk.verifyOrder
import processm.core.log.XESExtensionLoader.loadExtension
import processm.core.log.extension.Extension
import kotlin.test.*

class XESExtensionLoaderTest {
    @Test
    fun `read file from local resources with success`() {
        val extension: Extension? = loadExtension("https://xes-standard.org/time.xesext")

        assertNotNull(extension)
    }

    @Test
    fun `read file correctly parsed`() {
        val extension: Extension = loadExtension("http://www.xes-standard.org/time.xesext")!!

        assertEquals(extension.name, "Time")
        assertEquals(extension.prefix, "time")
        assertEquals(extension.uri, "http://www.xes-standard.org/time.xesext")

        assertEquals(extension.log.size, 0)
        assertEquals(extension.trace.size, 0)
        assertEquals(extension.meta.size, 0)
        assertEquals(extension.event.size, 1)

        val timestamp = extension.event.getValue("timestamp")

        assertEquals(timestamp.key, "timestamp")
        assertEquals(timestamp.type, "date")
        assertEquals(timestamp.aliases.size, 5)

        assertTrue(timestamp.aliases.keys.containsAll(listOf("en", "de", "fr", "pt", "es")))
    }

    @Test
    fun `can read locally stored file also when use capitalize`() {
        val extension: Extension = loadExtension("http://xes-standard.org/MICRO.xesext")!!

        assertEquals(extension.name, "Micro")
        assertEquals(extension.prefix, "micro")
        assertEquals(extension.uri, "http://www.xes-standard.org/micro.xesext")
    }

    @Test
    fun `can read from the Internet with success`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <xesextension name="Example" prefix="example" uri="http://example.com/example.xesext">
                <event>
                    <int key="level">
                        <alias mapping="EN" name="Example level of this event"/>
                    </int>
                </event>
            </xesextension>
        """.trimIndent().byteInputStream().use { content ->
            val mock = spyk<XESExtensionLoader>(recordPrivateCalls = true)
            every { mock["openExternalStream"]("http://example.com/example.xesext") } returns content

            val result = mock.loadExtension("http://example.com/example.xesext")!!

            verifyOrder {
                mock.loadExtension("http://example.com/example.xesext")
                mock["openExternalStream"]("http://example.com/example.xesext")
            }

            assertEquals(result.name, "Example")
            assertEquals(result.prefix, "example")
            assertEquals(result.uri, "http://example.com/example.xesext")
        }
    }

    @Test
    fun `not found resource`() {
        val mock = spyk<XESExtensionLoader>(recordPrivateCalls = true)
        every { mock["openExternalStream"]("http://example.com/no-resource.xesext") } returns null

        val result = mock.loadExtension("http://example.com/no-resource.xesext")

        verifyOrder {
            mock.loadExtension("http://example.com/no-resource.xesext")
            mock["openExternalStream"]("http://example.com/no-resource.xesext")
        }

        assertNull(result)
    }

    @Test
    fun `xml with invalid format (invalid opening tag) - ignore whole file`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <INVALID_TAG name="Example" prefix="example" uri="http://example.com/example.xesext">
                <event>
                    <int key="level">
                        <alias mapping="EN" name="Example level of this event"/>
                    </int>
                </event>
            </INVALID_TAG>
        """.trimIndent().byteInputStream().use { content ->
            val mock = spyk<XESExtensionLoader>(recordPrivateCalls = true)
            every { mock["openExternalStream"]("http://example.com/invalid-resource.xesext") } returns content

            val result = mock.loadExtension("http://example.com/invalid-resource.xesext")

            verifyOrder {
                mock.loadExtension("http://example.com/invalid-resource.xesext")
                mock["openExternalStream"]("http://example.com/invalid-resource.xesext")
            }

            assertNull(result)
        }
    }

    @Test
    fun `receive html instead of xml file - ignore whole file`() {
        "<html><body>Error page</body></html>".trimIndent().byteInputStream().use { content ->
            val mock = spyk<XESExtensionLoader>(recordPrivateCalls = true)
            every { mock["openExternalStream"]("http://example.com/html-resource.xesext") } returns content

            val result = mock.loadExtension("http://example.com/html-resource.xesext")

            verifyOrder {
                mock.loadExtension("http://example.com/html-resource.xesext")
                mock["openExternalStream"]("http://example.com/html-resource.xesext")
            }

            assertNull(result)
        }
    }

    @Test
    fun `xml with invalid tag inside - ignore it in structure`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <xesextension name="Example" prefix="example" uri="http://example.com/invalid-mapping-tag.xesext">
                <event>
                    <int key="level">
                        <INVALID-ALIAS-MAPPING mapping="EN" name="Example level of this event"/>
                    </int>
                </event>
            </xesextension>
        """.trimIndent().byteInputStream().use { content ->
            val mock = spyk<XESExtensionLoader>(recordPrivateCalls = true)
            every { mock["openExternalStream"]("http://example.com/invalid-mapping-tag.xesext") } returns content

            val result = mock.loadExtension("http://example.com/invalid-mapping-tag.xesext")!!

            verifyOrder {
                mock.loadExtension("http://example.com/invalid-mapping-tag.xesext")
                mock["openExternalStream"]("http://example.com/invalid-mapping-tag.xesext")
            }

            assertEquals(result.name, "Example")
            assertEquals(result.prefix, "example")
            assertEquals(result.uri, "http://example.com/invalid-mapping-tag.xesext")

            val levelAttr = result.event.getValue("level")

            assertEquals(levelAttr.key, "level")
            assertEquals(levelAttr.type, "int")
            assertEquals(levelAttr.aliases.size, 0)
        }
    }

    @Test
    fun `load extensions only one and store it in memory to reduce memory usage`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <xesextension name="Once" prefix="once" uri="http://example.com/only-once.xesext">
                <event>
                    <int key="level">
                        <alias mapping="EN" name="Example level of this event"/>
                    </int>
                </event>
            </xesextension>
        """.trimIndent().byteInputStream().use { content ->
            val mock = spyk<XESExtensionLoader>(recordPrivateCalls = true)
            every { mock["openExternalStream"]("http://example.com/only-once.xesext") } returns content

            mock.loadExtension("http://example.com/only-once.xesext")!!
            verifyOrder {
                mock.loadExtension("http://example.com/only-once.xesext")
                mock["openExternalStream"]("http://example.com/only-once.xesext")
            }

            val fromMemory = mock.loadExtension("http://example.com/only-once.xesext")!!
            verifyOrder {
                mock.loadExtension("http://example.com/only-once.xesext")
                mock["openExternalStream"]("http://example.com/only-once.xesext")?.wasNot(Called)
            }

            assertEquals(fromMemory.name, "Once")
            assertEquals(fromMemory.prefix, "once")
            assertEquals(fromMemory.uri, "http://example.com/only-once.xesext")
        }
    }

    @Test
    fun `org extension loaded successfully`() {
        val extension = XESExtensionLoader.org

        assertEquals(extension.name, "Organizational")
        assertEquals(extension.prefix, "org")
        assertEquals(extension.uri, "http://www.xes-standard.org/org.xesext")
    }

    @Test
    fun `cost extension loaded successfully`() {
        val extension = XESExtensionLoader.cost

        assertEquals(extension.name, "Cost")
        assertEquals(extension.prefix, "cost")
        assertEquals(extension.uri, "http://www.xes-standard.org/cost.xesext")
    }

    @Test
    fun `time extension loaded successfully`() {
        val extension = XESExtensionLoader.time

        assertEquals(extension.name, "Time")
        assertEquals(extension.prefix, "time")
        assertEquals(extension.uri, "http://www.xes-standard.org/time.xesext")
    }

    @Test
    fun `concept extension loaded successfully`() {
        val extension = XESExtensionLoader.concept

        assertEquals(extension.name, "Concept")
        assertEquals(extension.prefix, "concept")
        assertEquals(extension.uri, "http://www.xes-standard.org/concept.xesext")
    }

    @Test
    fun `identity time extension loaded successfully`() {
        val extension = XESExtensionLoader.identity

        assertEquals(extension.name, "Identity")
        assertEquals(extension.prefix, "identity")
        assertEquals(extension.uri, "http://www.xes-standard.org/identity.xesext")
    }

    @Test
    fun `lifecycle time extension loaded successfully`() {
        val extension = XESExtensionLoader.lifecycle

        assertEquals(extension.name, "Lifecycle")
        assertEquals(extension.prefix, "lifecycle")
        assertEquals(extension.uri, "http://www.xes-standard.org/lifecycle.xesext")
    }
}