package processm.core.esb

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
            close()
        })
    }

    fun register(vararg services: Service) {
        for (service in services) {
            if (servicesInternal.contains(service)) {
                throw IllegalArgumentException("Service $service is already registered.")
            }
        }

        for (service in services)
            servicesInternal[service] = service

        val jmxServer = ManagementFactory.getPlatformMBeanServer()

        // Register the services in individual threads rather than coroutines, as they may block a thread arbitrarily
        // long, and we do not want to block all threads in the thread pool.
        services.runConcurrent {
            it.register()
            jmxServer.registerMBean(it, ObjectName("$jmxDomain:0=services,name=${it.name}"))
        }
    }

    fun startAll() {
        // Start the services in individual threads rather than coroutines, as they may block a thread arbitrarily
        // long, and we do not want to block all threads in the thread pool.
        servicesInternal.keys.runConcurrent {
            it.start()
        }
    }

    fun stopAll() {
        // Stop the services in individual threads rather than coroutines, as they may block a thread arbitrarily
        // long, and we do not want to block all threads in the thread pool.
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
 * terminate.
 */
private fun Array<out Service>.runConcurrent(action: (Service) -> Unit) {
    this.map {
        thread {
            synchronized(it) {
                try {
                    action(it)
                } catch (e: Throwable) {
                    logger().error("", e)
                }
            }
        }
    }.forEach { it.join() }
}

/**
 * Runs concurrently the given action on all services. This function terminates when all concurrent invocations
 * terminate.
 */
private fun Iterable<out Service>.runConcurrent(action: (Service) -> Unit) {
    this.map {
        thread {
            synchronized(it) {
                try {
                    action(it)
                } catch (e: Throwable) {
                    logger().error("", e)
                }
            }
        }
    }.forEach { it.join() }
}


