package processm.core.esb

import org.jgrapht.alg.cycle.CycleDetector
import org.jgrapht.graph.DefaultDirectedGraph
import processm.helpers.forEachCatching
import processm.logging.loggedScope
import java.io.Closeable
import java.lang.management.ManagementFactory
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.management.ObjectName
import kotlin.concurrent.thread

/**
 * Enterprise Service Bus.
 */
class EnterpriseServiceBus : Closeable {
    companion object {
        private val instanceId = AtomicInteger(0)
        private const val jmxDomain = "processm"
        private fun getJMXDomain(): String =
            jmxDomain + instanceId.getAndIncrement().let { if (it == 0) "" else "-it" }

        internal fun computeDependencyGraph(services: Collection<Service>): DefaultDirectedGraph<Service, Int> {
            val graph = DefaultDirectedGraph<Service, Int>(Int::class.java)
            var ctr = 0
            services.forEach { graph.addVertex(it) }
            for (service in services) {
                for (dependency in services) {
                    if (service.dependencies.any { dependencyClass -> dependencyClass.isInstance(dependency) })
                        graph.addEdge(dependency, service, ctr++)
                }
            }
            return graph
        }

    }

    /**
     * The JMX for this instance of [EnterpriseServiceBus].
     */
    val jmxDomain = getJMXDomain()

    /**
     * List of all registered services. Uniqueness is ensured.
     */
    private val servicesInternal = IdentityHashMap<Service, Service>()

    /**
     *  List of all registered services.
     */
    val services: Set<Service> = Collections.unmodifiableSet(servicesInternal.keys)

    private lateinit var dependencyGraph: DefaultDirectedGraph<Service, Int>

    init {
        Runtime.getRuntime().addShutdownHook(thread(false) {
            loggedScope { logger ->
                try {
                    logger.info("Shutting down services")
                    close()
                } catch (e: Throwable) {
                    // We can't do anything anyway
                    logger.warn("Exception during finalization", e)
                }
            }
        })
    }

    /**
     * Automatically finds and registers services on classpath.
     * @see register
     */
    fun autoRegister() = loggedScope { logger ->
        logger.debug("Detecting and registering services")
        val services = ServiceLoader.load(Service::class.java)
        this.register(*services.toList().toTypedArray())
    }

    /**
     * Registers, possibly concurrently, the given services by calling register() and registering MXBean
     * for each service. If an exception occurs during registration, the service will not be registered
     * and the exception is rethrown. If many exceptions occur for many services, the first one is rethrown
     * and the successive are suppressed.
     * Every service can be registered only once.
     *
     * It is assumed, though not enforced, that every registered service is of different class.
     * @param services The array of services to register
     * @see java.lang.Throwable.getSuppressed
     */
    fun register(vararg services: Service) {
        synchronized(servicesInternal) {
            for (service in services) {
                if (servicesInternal.contains(service)) {
                    throw IllegalArgumentException("Service ${service.name} is already registered.")
                }
            }

            try {
                val jmxServer = ManagementFactory.getPlatformMBeanServer()
                services.forEachCatching { service ->
                    service.register()
                    jmxServer.registerMBean(service, ObjectName("$jmxDomain:0=services,name=${service.name}"))
                    servicesInternal[service] = service
                }
            } finally {
                dependencyGraph = computeDependencyGraph(servicesInternal.keys)
                require(!CycleDetector(dependencyGraph).detectCycles()) { "The dependency graph became cyclic." }
            }
        }
    }

    /**
     * Starts a service.
     */
    fun start(service: Service): Unit = loggedScope { logger ->
        synchronized(servicesInternal) {
            require(service in servicesInternal) { "Service ${service.name} is not registered." }
            require(service.status == ServiceStatus.Stopped) { "Service ${service.name} is already started." }
            dependencyGraph.incomingEdgesOf(service).forEach { edge ->
                val dependency = dependencyGraph.getEdgeSource(edge)
                if (dependency.status == ServiceStatus.Stopped)
                    start(dependency)
            }
            logger.info("Starting service ${service.name}...")
            service.start()
            logger.info("Service ${service.name} started.")
        }
    }

    fun stop(service: Service): Unit = loggedScope { logger ->
        synchronized(servicesInternal) {
            require(service in servicesInternal) { "Service ${service.name} is not registered." }
            require(service.status == ServiceStatus.Started) { "Service ${service.name} is already stopped." }
            dependencyGraph.outgoingEdgesOf(service).forEach { edge ->
                val dependee = dependencyGraph.getEdgeTarget(edge)
                if (dependee.status == ServiceStatus.Started)
                    stop(dependee)
            }
            logger.info("Stopping service ${service.name}...")
            service.stop()
            logger.info("Service ${service.name} stopped.")
        }
    }

    /**
     * Starts, possibly concurrently, all registered services. If an exception occurs during startup,
     * the service will not be started and the exception is rethrown. If many exceptions occur for many
     * services, the first one is rethrown and the successive are suppressed.
     * @see java.lang.Throwable.getSuppressed
     */
    fun startAll() {
        synchronized(servicesInternal) {
            servicesInternal.keys.forEachCatching { service ->
                if (service.status == ServiceStatus.Stopped)
                    start(service)
            }
        }
    }

    /**
     * Stops, possibly concurrently, all registered services. If an exception occurs during stopping,
     * the service may not be stopped and the exception is rethrown. If many exceptions occur for many
     * services, the first one is rethrown and the successive are suppressed.
     * @see java.lang.Throwable.getSuppressed
     */
    fun stopAll() {
        synchronized(servicesInternal) {
            servicesInternal.keys.forEachCatching { service ->
                if (service.status == ServiceStatus.Started)
                    stop(service)
            }
        }
    }

    override fun close() {
        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        synchronized(servicesInternal) {
            servicesInternal.keys.forEachCatching { service ->
                if (service.status == ServiceStatus.Started)
                    stop(service)
                jmxServer.unregisterMBean(ObjectName("$jmxDomain:0=services,name=${service.name}"))
            }
            servicesInternal.clear()
        }
    }
}

