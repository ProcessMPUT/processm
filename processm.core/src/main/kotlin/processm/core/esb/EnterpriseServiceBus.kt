package processm.core.esb

import processm.core.logging.logger
import java.io.Closeable
import java.lang.management.ManagementFactory
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import javax.management.ObjectName
import kotlin.concurrent.thread

/**
 * Enterprise Service Bus.
 */
class EnterpriseServiceBus : Closeable {
    companion object {
        private const val jmxDomain = "processm"
    }

    /**
     * List of all registered services. Uniqueness is ensured.
     */
    private val servicesInternal = IdentityHashMap<Service, Service>()

    /**
     *  List of all registered services.
     */
    val services: Set<Service> = Collections.unmodifiableSet(servicesInternal.keys)

    init {
        Runtime.getRuntime().addShutdownHook(thread(false) {
            try {
                logger().info("Shutting down services")
                close()
            } catch (e: Throwable) {
                // We can't do anything anyway
                logger().warn("Exception during finalization", e)
            }
        })
    }

    /**
     * Automatically finds and registers services on classpath.
     * @see register
     */
    fun autoRegister() {
        logger().debug("Detecting and registering services")
        val services = ServiceLoader.load(Service::class.java)
        this.register(*services.toList().toTypedArray())
    }

    /**
     * Registers, possibly concurrently, the given services by calling register() and registering MXBean
     * for each service. If an exception occurs during registration, the service will not be registered
     * and the exception is rethrown. If many exceptions occur for many services, the first one is rethrown
     * and the successive are suppressed.
     * Every service can be registered only once.
     * @param services The array of services to register
     * @see java.lang.Throwable.getSuppressed
     */
    fun register(vararg services: Service) {
        for (service in services) {
            if (servicesInternal.contains(service)) {
                throw IllegalArgumentException("Service $service is already registered.")
            }
        }

        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        services.asIterable().runConcurrent {
            it.register()
            jmxServer.registerMBean(it, ObjectName("$jmxDomain:0=services,name=${it.name}"))
            synchronized(servicesInternal) {
                servicesInternal[it] = it
            }
        }
    }

    /**
     * Starts, possibly concurrently, all registered services. If an exception occurs during registration,
     * the service will not be registered and the exception is rethrown. If many exceptions occur for many
     * services, the first one is rethrown and the successive are suppressed.
     * @see java.lang.Throwable.getSuppressed
     */
    fun startAll() {
        servicesInternal.keys.runConcurrent {
            if (it.status == ServiceStatus.Stopped)
                it.start()
        }
    }

    /**
     * Stops, possibly concurrently, all registered services. If an exception occurs during registration,
     * the service will not be registered and the exception is rethrown. If many exceptions occur for many
     * services, the first one is rethrown and the successive are suppressed.
     * @see java.lang.Throwable.getSuppressed
     */
    fun stopAll() {
        servicesInternal.keys.runConcurrent {
            if (it.status == ServiceStatus.Started)
                it.stop()
        }
    }

    override fun close() {
        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        servicesInternal.keys.runConcurrent {
            if (it.status == ServiceStatus.Started)
                it.stop()
            jmxServer.unregisterMBean(ObjectName("$jmxDomain:0=services,name=${it.name}"))
        }
        servicesInternal.clear()
    }
}

/**
 * Runs concurrently the given action on all services. This function terminates when all concurrent invocations
 * terminate. The exceptions caught in individual threads are rethrown by this function.
 */
private fun Iterable<Service>.runConcurrent(action: (Service) -> Unit) {
    // DO NOT use coroutines here, as they may be canceled by exceptions in other coroutines in the same scope.
    // See e.g.,
    // https://medium.com/the-kotlin-chronicle/coroutine-exceptions-3378f51a7d33
    // https://kotlinlang.org/docs/reference/coroutines/exception-handling.html
    // TODO: replace common pool with dedicated one if required
    val pool = ForkJoinPool.commonPool()
    val tasks = this.map { pool.submit { synchronized(it) { action(it) } } }
    val exceptions = tasks.mapNotNull { it.runCatching { get() }.exceptionOrNull() }
    var exception: Throwable? = null
    for (e in exceptions) {
        if (e is ExecutionException) {
            // collect exceptions from tasks and suppress successive exceptions
            if (exception === null)
                exception = e.cause
            else
                exception.addSuppressed(e.cause)
        } else {
            throw e // throw everything else
        }
    }
    if (exception !== null)
        throw exception
}


