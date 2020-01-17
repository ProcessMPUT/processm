package processm.core.esb

import kotlinx.coroutines.*
import processm.core.logging.logger
import java.io.Closeable
import java.lang.IllegalArgumentException
import java.lang.management.ManagementFactory
import java.util.*
import javax.management.ObjectName
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

/**
 * Enterprise Service Bus.
 */
class EnterpriseServiceBus : Closeable {
    private val jmxDomain = "processm"

    /**
     * List of all registered services. Uniqueness is ensured.
     */
    private val servicesInternal = IdentityHashMap<Service, Service>()

    /**
     *  List of all registered services.
     */
    val services: Set<Service>
        get() = Collections.unmodifiableSet(servicesInternal.keys)

    init {
        Runtime.getRuntime().addShutdownHook(thread(false) {
            try {
                close()
            } catch (e: Throwable) {
                // We can't do anything anyway
                logger().warn("Exception during finalization", e)
            }
        })
    }

    /**
     * Registers, possibly concurrently, the given services by calling register() and registering MXBean
     * for each service. If an exception occurs during registration, the service will not be registered
     * and the exception is rethrown. If many exceptions occur for many services, the first one is rethrown
     * and the successive are suppressed.
     * @see java.lang.Throwable.getSuppressed
     */
    fun register(vararg services: Service) {
        for (service in services) {
            if (servicesInternal.contains(service)) {
                throw IllegalArgumentException("Service $service is already registered.")
            }
        }

        val jmxServer = ManagementFactory.getPlatformMBeanServer()
        services.runConcurrent {
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
 * terminate. The exceptions caught in the coroutines are rethrown by this function.
 */
private fun Array<out Service>.runConcurrent(action: (Service) -> Unit) = runBlocking {
    // await() ensures that all exceptions thrown in coroutines will be rethrown in the calling thread
    // (the first-thrown exception will be rethrown, and the remaining will be reported as suppressed exceptions).
    GlobalScope.async {
        forEach { launch { synchronized(it) { action(it) } } }
    }.await()
}

/**
 * Runs concurrently the given action on all services. This function terminates when all concurrent invocations
 * terminate. The exceptions caught in the coroutines are rethrown by this function.
 */
private fun Iterable<Service>.runConcurrent(action: (Service) -> Unit) = runBlocking {
    // await() ensures that all exceptions thrown in coroutines will be rethrown in the calling thread
    // (the first-thrown exception will be rethrown, and the remaining will be reported as suppressed exceptions).
    GlobalScope.async {
        forEach { launch { synchronized(it) { action(it) } } }
    }.await()
}


