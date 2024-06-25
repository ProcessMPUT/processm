package processm.dbmodels.models

import io.mockk.every
import io.mockk.mockk
import java.net.URI
import kotlin.test.*

class ProcessModelComponentDataTest {

    private lateinit var emptyComponent: WorkspaceComponent

    @BeforeTest
    fun setup() {
        emptyComponent = mockk<WorkspaceComponent> {
            every { data } returns "{}"
        }
    }

    @Test
    fun `factory sets the component`() {
        with(ProcessModelComponentData.create(emptyComponent)) {
            assertSame(emptyComponent, this.component)
        }
    }

    @Test
    fun `acceptedModelId corresponds to acceptedModelVersion`() {
        with(ProcessModelComponentData.create(emptyComponent)) {
            assertNull(acceptedModelId)
            addModel(1, "v1")
            addModel(2, "v2")
            acceptedModelVersion = 2
            assertEquals("v2", acceptedModelId)
            acceptedModelVersion = 1
            assertEquals("v1", acceptedModelId)
        }
    }

    @Test
    fun `addAlignmentKPIReport inserts or replaces`() {
        with(ProcessModelComponentData.create(emptyComponent)) {
            assertTrue { alignmentKPIReports.isEmpty() }
            addAlignmentKPIReport(1, 2, URI("test:v1"))
            assertEquals(mapOf(1L to mapOf(2L to URI("test:v1"))), alignmentKPIReports)
            addAlignmentKPIReport(1, 3, URI("test:v2"))
            assertEquals(mapOf(1L to mapOf(2L to URI("test:v1"), 3L to URI("test:v2"))), alignmentKPIReports)
            addAlignmentKPIReport(4, 3, URI("test:v3"))
            assertEquals(
                mapOf(
                    1L to mapOf(2L to URI("test:v1"), 3L to URI("test:v2")),
                    4L to mapOf(3L to URI("test:v3"))
                ), alignmentKPIReports
            )
            addAlignmentKPIReport(1, 3, URI("test:v4"))
            assertEquals(
                mapOf(
                    1L to mapOf(2L to URI("test:v1"), 3L to URI("test:v4")),
                    4L to mapOf(3L to URI("test:v3"))
                ), alignmentKPIReports
            )
        }
    }

    @Test
    fun `getAlignmentKPIReport returns URI or null`() {
        val component = mockk<WorkspaceComponent> {
            every { data } returns """{"alignment_kpi_report": {"1": {"2": "test:v1", "3": "test:v2"}, "4": {"5": "test:v3"}}}"""
        }
        with(ProcessModelComponentData.create(component)) {
            assertEquals("test:v1", getAlignmentKPIReport(1, 2)?.toString())
            assertEquals("test:v2", getAlignmentKPIReport(1, 3)?.toString())
            assertNull(getAlignmentKPIReport(1, 4))
            assertEquals("test:v3", getAlignmentKPIReport(4, 5)?.toString())
            assertNull(getAlignmentKPIReport(2, 2))
            assertNull(getAlignmentKPIReport(123, 456))
        }
    }

    @Test
    fun `getMostRecentAlignmentKPIReport returns null if no model is specified nor accepted`() {
        with(ProcessModelComponentData.create(emptyComponent)) {
            assertNull(getMostRecentAlignmentKPIReport())
        }
    }


    @Test
    fun `getMostRecentAlignmentKPIReport returns the most recent alignment`() {
        val component = mockk<WorkspaceComponent> {
            every { data } returns """{"accepted_model_version": 1, "alignment_kpi_report": {"1": {"2": "test:v1", "3": "test:v2"}, "4": {"5": "test:v3"}}}"""
        }
        with(ProcessModelComponentData.create(component)) {
            assertEquals("test:v3", getMostRecentAlignmentKPIReport(4)?.toString())
            assertEquals("test:v2", getMostRecentAlignmentKPIReport()?.toString())
            assertNull(getMostRecentAlignmentKPIReport(0xc00ffee))
        }
    }

    @Test
    fun `addModel autoaccepts only if no model was accepted`() {
        with(ProcessModelComponentData.create(emptyComponent)) {
            assertNull(acceptedModelVersion)
            assertTrue { addModel(1, "v1") }
            assertEquals(1, acceptedModelVersion)
            assertFalse { addModel(2, "v2") }
            assertEquals(1, acceptedModelVersion)
            assertEquals(mapOf(1L to "v1", 2L to "v2"), models)
        }
    }

    @Test
    fun `accepting an unknown model throws`() {
        with(ProcessModelComponentData.create(emptyComponent)) {
            assertFailsWith<IllegalArgumentException> { acceptedModelVersion = 123 }
        }
    }

    @Test
    fun hasModel() {
        val component = mockk<WorkspaceComponent> {
            every { data } returns """{"models": {"1": "v1", "2": "v2"}}"""
        }
        with(ProcessModelComponentData.create(component)) {
            assertTrue { hasModel(1L) }
            assertTrue { hasModel(2L) }
            assertFalse { hasModel(0xc00ffee) }
        }
    }
}