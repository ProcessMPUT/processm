package processm.core.esb

import io.mockk.*
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import javax.management.MBeanServer
import javax.management.MXBean
import javax.management.ObjectName
import kotlin.reflect.KClass
import kotlin.test.*

class EnterpriseServiceBusTest {

    private lateinit var esb: EnterpriseServiceBus

    @BeforeTest
    fun setUp() {
        esb = EnterpriseServiceBus()
    }

    @AfterTest
    fun cleanUp() {
        esb.close()
    }

    @Test
    fun registerTest() {
        val ts = arrayOf(TestService("1"), TestService("2"), TestService("3"))
        esb.register(ts[0])

        assertEquals(ts[0], esb.services.first())
        assertEquals(ServiceStatus.Stopped, ts[0].status)
        assertEquals(1, esb.services.size)

        esb.register(ts[1], ts[2])
        for (s in ts) {
            assertTrue(esb.services.contains(s))
            assertEquals(ServiceStatus.Stopped, s.status)
        }

        assertFailsWith(IllegalArgumentException::class) {
            esb.register(ts[0], ts[1], ts[2])
        }
    }

    @Test
    fun startAndStopAllTest() {
        val ts = arrayOf(TestService("1"), TestService("2"), TestService("3"))
        esb.register(ts[0], ts[1], ts[2])

        esb.startAll()
        for (s in ts) {
            assertEquals(ServiceStatus.Started, s.status)
        }

        esb.stopAll()
        for (s in ts) {
            assertEquals(ServiceStatus.Stopped, s.status)
        }
    }

    @Test
    fun startAndStopDependenciesFirst() {
        val ts1 = CustomService("1")
        val ts2 = CustomService2("2", dependencies = listOf(CustomService::class))
        val ts3 = TestService("3", dependencies = listOf(CustomService2::class))
        val ts4 = FailingService("4", ServiceStatus.Stopped)

        esb.register(ts1, ts2, ts3, ts4)

        esb.start(ts1)
        assertEquals(ServiceStatus.Started, ts1.status)
        assertEquals(ServiceStatus.Stopped, ts2.status)
        assertEquals(ServiceStatus.Stopped, ts3.status)
        assertEquals(ServiceStatus.Stopped, ts4.status)

        esb.stop(ts1)
        assertEquals(ServiceStatus.Stopped, ts1.status)
        assertEquals(ServiceStatus.Stopped, ts2.status)
        assertEquals(ServiceStatus.Stopped, ts3.status)
        assertEquals(ServiceStatus.Stopped, ts4.status)

        esb.start(ts3)
        assertEquals(ServiceStatus.Started, ts1.status)
        assertEquals(ServiceStatus.Started, ts2.status)
        assertEquals(ServiceStatus.Started, ts3.status)
        assertEquals(ServiceStatus.Stopped, ts4.status)

        esb.stop(ts3)
        assertEquals(ServiceStatus.Started, ts1.status)
        assertEquals(ServiceStatus.Started, ts2.status)
        assertEquals(ServiceStatus.Stopped, ts3.status)
        assertEquals(ServiceStatus.Stopped, ts4.status)

        esb.stop(ts1)
        assertEquals(ServiceStatus.Stopped, ts1.status)
        assertEquals(ServiceStatus.Stopped, ts2.status)
        assertEquals(ServiceStatus.Stopped, ts3.status)
        assertEquals(ServiceStatus.Stopped, ts4.status)
    }

    @Test
    fun jmxAvailabilityTest() {
        val ts = arrayOf(TestService("1"), TestService("2"), TestService("3"))
        esb.register(ts[0], ts[1], ts[2])

        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        for (s in ts) {
            val name = jmxServer.getAttribute(ObjectName("${esb.jmxDomain}:0=services,name=${s.name}"), "Name")
            assertEquals(s.name, name)

            val status =
                jmxServer.getAttribute(ObjectName("${esb.jmxDomain}:0=services,name=${s.name}"), "Status") as String
            assertEquals(s.status, ServiceStatus.valueOf(status))
        }
    }

    @Test
    fun jmxCustomService() {
        val service = CustomService("myCustomService")
        esb.register(service)

        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        val mbean = jmxServer.getMBeanInfo(ObjectName("${esb.jmxDomain}:0=services,name=${service.name}"))

        assertTrue(mbean.attributes.any { it.name == CustomMXBean::myCustomField.name.capitalize() })
    }

    @Test
    fun testThrowInRegister() {
        val normalService1 = TestService("myNormalService1", 0)
        val normalService2 = TestService("myNormalService2", 2000)
        val failingService = FailingService("myFailingService", ServiceStatus.Unknown, 1000)

        assertFailsWith(IllegalStateException::class) {
            esb.register(normalService1, failingService, normalService2)
        }

        assertEquals(2, esb.services.size)
        assertTrue(esb.services.any { it.name == normalService1.name })
        assertTrue(esb.services.any { it.name == normalService2.name })
        assertFalse(esb.services.any { it.name == failingService.name })
    }

    @Test
    fun testThrowInStart() {
        val normalService = TestService("myNormalService")
        val failingService = FailingService("myFailingService", ServiceStatus.Started)

        esb.register(failingService, normalService)
        assertEquals(2, esb.services.size)
        assertTrue(normalService in esb.services)
        assertTrue(failingService in esb.services)

        assertFailsWith(IllegalStateException::class) {
            esb.startAll()
        }
    }

    @Test
    fun testThrowInStop() {
        val normalService = TestService("myNormalService")
        val failingService = FailingService("myFailingService", ServiceStatus.Stopped)

        esb.register(failingService, normalService)
        assertEquals(2, esb.services.size)
        assertTrue(normalService in esb.services)
        assertTrue(failingService in esb.services)

        esb.startAll()

        assertFailsWith(IllegalStateException::class) {
            esb.stopAll()
        }

        // prevent exception throw on clean up
        failingService._when = ServiceStatus.Unknown
    }

    @Test
    fun autoRegisterTest() {
        // Verify whether it automatically detected standard services like Artemis and Hawtio
        esb.autoRegister()
        assertTrue(esb.services.any { it is Artemis })
        assertTrue(esb.services.any { it is Hawtio })
    }

    /**
     * This test repeats construction and destruction of the ESB multiple times running gc between the repetitions.
     * It should execute quickly and without noticeable memory consumption.
     */
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @Test
    fun `resource consumption`() {
        fun s(name: String, vararg deps: Service) = mockk<Service> {
            var status = ServiceStatus.Stopped
            every { this@mockk.name } returns name
            val slot = slot<Service>()
            every { dependencies } returns deps.map { dep ->
                mockk<KClass<out Service>> {
                    every { isInstance(capture(slot)) } answers { slot.captured === dep }
                }
            }
            every { register() } just Runs
            every { this@mockk.status } answers { status }
            every { start() } answers { status = ServiceStatus.Started }
            every { stop() } answers { status = ServiceStatus.Stopped }
        }

        val artemis = s("Artemis")
        val services = listOf(
            s("Causal net", artemis),
            s("MetaModel to XES conversion", artemis),
            s("Hawtio"),
            s("JDBC-based ETL", artemis),
            s("MetaModel ETL", artemis),
            s("e-mail", artemis),
            s("WebServicesHost"),
            s("Directly-follows graph", artemis),
            s("Aligner-based KPI", artemis),
            artemis,
            s("Petri net", artemis),
            s("Log-based KPI", artemis)
        )
        try {
            val mBean = mockk<MBeanServer>(relaxed = true)
            mockkStatic(ManagementFactory::class)
            every { ManagementFactory.getPlatformMBeanServer() } returns mBean
            for (i in 1..10) {
                System.gc()
                EnterpriseServiceBus().use { esb ->
                    esb.register(*services.toTypedArray())
                    esb.startAll()
                }
            }
        } finally {
            clearStaticMockk(ManagementFactory::class)
        }
    }

    @Test
    fun cyclicDeps() {
        val services = listOf(
            CustomService("s1", listOf(CustomService2::class)),
            CustomService2("s2", listOf(CustomService::class)),
        )
        try {
            val mBean = mockk<MBeanServer>(relaxed = true)
            mockkStatic(ManagementFactory::class)
            every { ManagementFactory.getPlatformMBeanServer() } returns mBean
            assertThrows<RuntimeException> {
                EnterpriseServiceBus().use { esb ->
                    esb.register(*services.toTypedArray())
                    esb.startAll()
                }
            }
        } finally {
            clearStaticMockk(ManagementFactory::class)
        }
    }

    @Test
    fun `two instances of the same dependency`() {
        val services = listOf(
            CustomService("s1a"),
            CustomService("s1b"),
            CustomService2("s2", listOf(CustomService::class)),
        )
        try {
            val mBean = mockk<MBeanServer>(relaxed = true)
            mockkStatic(ManagementFactory::class)
            every { ManagementFactory.getPlatformMBeanServer() } returns mBean
            EnterpriseServiceBus().use { esb ->
                esb.register(*services.toTypedArray())
                esb.startAll()
            }
        } finally {
            clearStaticMockk(ManagementFactory::class)
        }
    }
}

open class TestService(
    override val name: String,
    val delay: Long = 0,
    override val dependencies: List<KClass<out Service>> = emptyList()
) : Service {
    override var status = ServiceStatus.Unknown
        protected set

    override fun register() {
        Thread.sleep(delay)
        status = ServiceStatus.Stopped
    }

    override fun start() {
        Thread.sleep(delay)
        status = ServiceStatus.Started
    }

    override fun stop() {
        Thread.sleep(delay)
        status = ServiceStatus.Stopped
    }
}

@MXBean
interface CustomMXBean : ServiceMXBean {
    val myCustomField: Int
}

class CustomService(name: String, dependencies: List<KClass<out Service>> = emptyList()) :
    TestService(name, dependencies = dependencies), CustomMXBean {
    override val myCustomField: Int
        get() = 1
}

class CustomService2(name: String, dependencies: List<KClass<out Service>> = emptyList()) :
    TestService(name, dependencies = dependencies), CustomMXBean {
    override val myCustomField: Int
        get() = 2
}

class FailingService(name: String, var _when: ServiceStatus, val failDelay: Long = 0) : TestService(name) {
    override fun register() {
        if (_when == ServiceStatus.Unknown) {
            Thread.sleep(failDelay)
            throw IllegalStateException()
        }
        super.register()
    }

    override fun start() {
        if (_when == ServiceStatus.Started) {
            Thread.sleep(failDelay)
            throw IllegalStateException()
        }
        super.start()
    }

    override fun stop() {
        if (_when == ServiceStatus.Stopped) {
            Thread.sleep(failDelay)
            throw IllegalStateException()
        }
        super.stop()
    }
}
