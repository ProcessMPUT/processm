package processm.core.esb

import java.lang.management.ManagementFactory
import javax.management.ObjectName
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
    fun jmxAvailabilityTest() {
        val ts = arrayOf(TestService("1"), TestService("2"), TestService("3"))
        esb.register(ts[0], ts[1], ts[2])

        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        for (s in ts) {
            val name = jmxServer.getAttribute(ObjectName("processm:0=services,name=${s.name}"), "Name")
            assertEquals(s.name, name)

            val status = jmxServer.getAttribute(ObjectName("processm:0=services,name=${s.name}"), "Status") as String
            assertEquals(s.status, ServiceStatus.valueOf(status))
        }
    }

    @Test
    fun jmxCustomService() {
        val service = CustomService("myCustomService")
        esb.register(service)

        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        val mbean = jmxServer.getMBeanInfo(ObjectName("processm:0=services,name=${service.name}"))

        assertTrue(mbean.attributes.any { it.name == CustomMXBean::myCustomField.name.capitalize() })
    }

    @Test
    fun testThrowInRegister() {
        val normalService = TestService("myNormalService")
        val failingService = FailingService("myFailingService", ServiceStatus.Unknown)

        assertFailsWith(IllegalStateException::class) {
            esb.register(failingService, normalService)
        }

        assertEquals(1, esb.services.size)
        assertEquals(normalService, esb.services.first())
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
        // Verify whether it automatically detected standard services like Artemis
        val esb = EnterpriseServiceBus()
        esb.autoRegister()
        assertTrue(esb.services.any { it is Artemis })
    }
}

open class TestService(override val name: String) : Service {
    override var status = ServiceStatus.Unknown
        protected set

    override fun register() {
        status = ServiceStatus.Stopped
    }

    override fun start() {
        status = ServiceStatus.Started
    }

    override fun stop() {
        status = ServiceStatus.Stopped
    }
}

interface CustomMXBean : Service {
    val myCustomField: Int
}

class CustomService(name: String) : TestService(name), CustomMXBean {
    override val myCustomField: Int
        get() = 1
}

class FailingService(name: String, var _when: ServiceStatus) : TestService(name) {
    override fun register() {
        if (_when == ServiceStatus.Unknown)
            throw IllegalStateException()
        super.register()
    }

    override fun start() {
        if (_when == ServiceStatus.Started)
            throw IllegalStateException()
        super.start()
    }

    override fun stop() {
        if (_when == ServiceStatus.Stopped)
            throw IllegalStateException()
        super.stop()
    }
}