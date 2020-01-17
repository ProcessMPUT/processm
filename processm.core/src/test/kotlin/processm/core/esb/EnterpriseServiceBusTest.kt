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
        TODO()
    }

    @Test
    fun testThrowInStart() {
        TODO()
    }

    @Test
    fun testThrowInStop() {
        TODO()
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

class CustomService : TestService, CustomMXBean {
    override val myCustomField: Int
        get() = 1

    constructor(name: String) : super(name) {}

}
