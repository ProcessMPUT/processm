package processm.core.esb

import processm.core.helpers.forEachCatching
import processm.core.logging.loggedScope
import processm.core.models.commons.Activity
import processm.core.models.petrinet.*
import java.io.Closeable
import java.lang.management.ManagementFactory
import java.util.*
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

    private lateinit var petriNet: PetriNet
    private var petriNetInstance: PetriNetInstance? = null

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
                petriNet = getServicePetriNet(servicesInternal.keys)
                petriNetInstance = PetriNetInstance(petriNet)
                if (servicesInternal.keys.any { s -> s.status != ServiceStatus.Stopped })
                    TODO("The calculation of the marking for running services is not implemented.")
            }
        }
    }

    /**
     * Starts a service.
     */
    fun start(service: Service): Unit = loggedScope { logger ->
        synchronized(servicesInternal) {
            require(service in servicesInternal) { "Service ${service.name} is not registered." }
            val servicePlace = petriNet.places.first { (it as? ServicePlace)?.service === service } as ServicePlace
            require(servicePlace !in petriNetInstance!!.currentState) { "Service ${service.name} is already started." }
            assert(service.status == ServiceStatus.Stopped)

            val path = findPath(servicePlace, true)
            for (transition in path) {
                if (transition.service.status == ServiceStatus.Stopped) {
                    logger.info("Starting service ${transition.service.name}...")
                    transition.service.start()
                    petriNetInstance!!.getExecutionFor(transition).execute()
                    logger.info("Service ${transition.service.name} started.")
                }
            }
        }
    }

    fun stop(service: Service): Unit = loggedScope { logger ->
        synchronized(servicesInternal) {
            require(service in servicesInternal) { "Service ${service.name} is not registered." }
            val servicePlace = petriNet.places.first { (it as? ServicePlace)?.service === service } as ServicePlace
            require(servicePlace in petriNetInstance!!.currentState) { "Service ${service.name} is already stopped." }
            assert(service.status == ServiceStatus.Started)

            val path = findPath(servicePlace, false)
            for (transition in path) {
                if (transition.service.status == ServiceStatus.Started) {
                    logger.info("Stopping service ${service.name}...")
                    transition.service.stop()
                    petriNetInstance!!.getBackwardExecutionFor(transition).execute()
                    logger.info("Service ${service.name} stopped.")
                }
            }
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

    /**
     * For [forward]=true, it finds the shortest path from the current marking to the marking, where token lies in the
     * end place corresponding to service X (the list of services to run resulting in X running).
     * For [forward]=false, it finds the shortest path from the current marking to the marking, where no token lies in
     * the end place corresponding to transition X (the list of services to stop, resulting in X stopped).
     */
    private fun findPath(toPlace: ServicePlace, forward: Boolean): List<ServiceTransition> {
        val petriNetInstance = this.petriNetInstance!!
        val initialMarking = petriNetInstance.currentState
        val queue = PriorityQueue<SearchState>()
        queue.add(SearchState(petriNetInstance.currentState.copy()))

        while (queue.isNotEmpty()) {
            var state = queue.poll()

            if (state.marking.contains(toPlace) == forward) {
                val out = ArrayList<ServiceTransition>()
                state = state.previousState
                while (state !== null) {
                    state.activity?.let { out.add(it as ServiceTransition) }
                    state = state.previousState
                }
                out.reverse()

                petriNetInstance.setState(initialMarking)

                assert(out.isNotEmpty() || toPlace in initialMarking)
                return out
            }

            petriNetInstance.setState(state.marking.copy())
            if (state.activity !== null) {
                if (forward)
                    petriNetInstance.getExecutionFor(state.activity!!).execute()
                else
                    petriNetInstance.getBackwardExecutionFor(state.activity as Transition).execute()
            }

            val activities =
                if (forward) petriNetInstance.availableActivities
                else petriNetInstance.backwardAvailableActivities


            var added = state.activity === null
            for (activity in activities) {
                queue.add(
                    SearchState(
                        marking = petriNetInstance.currentState,
                        activity = activity,
                        cost = state.cost + (if ((activity as ServiceTransition).service == toPlace.service) 0 else 1),
                        previousState = state
                    )
                )
                added = true
            }

            if (!added) {
                queue.add(
                    SearchState(
                        marking = petriNetInstance.currentState,
                        activity = null,
                        cost = state.cost,
                        previousState = state
                    )
                )
            }
        }
        throw IllegalStateException("Cannot find the path to ${if (forward) "put" else "clear"} the token in $toPlace.")
    }

    /**
     * Builds a Petri net of services, representing the dependencies of services. Each service corresponds to
     * a transition. The Petri net has as many start and end places as services. The transition is
     * associated with at least one input place, either connecting the transition with its dependency or being a start place.
     * The transition is associated with at least one output place. The output place connected to another transition
     * corresponds to the dependency. The output place disconnected from other transitions is the end place.
     * The existence of a token in the end place of a transition indicates that the corresponding service is running.
     * The start marking consists of a single token in each start place. The final marking consists of a single token
     * in each end place.
     */
    private fun getServicePetriNet(services: Collection<Service>): PetriNet = loggedScope { logger ->
        val places = mutableListOf<Place>()
        val initialMarking = mutableListOf<Place>()
        val finalMarking = mutableListOf<Place>()
        val transitions = services.map {
            val inPlace = Place()
            initialMarking.add(inPlace)
            val outPlace = ServicePlace(it)
            finalMarking.add(outPlace)

            ServiceTransition(
                service = it,
                inPlaces = mutableListOf(inPlace),
                outPlaces = mutableListOf(outPlace)
            )
        }
        // add dependencies
        for (transition in transitions) {
            val dependencies = transition.service.dependencies
            val transitionDependencies = dependencies.map { s -> transitions.first { t -> s.isInstance(t.service) } }
            for (transitionDependency in transitionDependencies) {
                val inPlace = Place()
                places.add(inPlace)
                (transition.inPlaces as MutableList<Place>).add(inPlace)
                (transitionDependency.outPlaces as MutableList<Place>).add(inPlace)
            }
        }

        places.addAll(initialMarking)
        places.addAll(finalMarking)

        assert(transitions.all { it.inPlaces.isNotEmpty() })
        assert(transitions.all { it.outPlaces.isNotEmpty() })
        assert(finalMarking.size >= transitions.size)
        assert(transitions.size == services.size)

        val net = PetriNet(
            places = places,
            transitions = transitions,
            initialMarking = Marking(*initialMarking.toTypedArray()),
            finalMarking = Marking(*finalMarking.toTypedArray())
        )

        logger.debug("Built the Petri net of services:\n${net.toMultilineString()}")
        return net
    }

    private class ServiceTransition(val service: Service, inPlaces: MutableList<Place>, outPlaces: MutableList<Place>) :
        Transition(service.name, inPlaces, outPlaces)

    private class ServicePlace(val service: Service) : Place()

    private data class SearchState(
        /**
         * The marking before executing [execution].
         */
        val marking: Marking,
        val activity: Activity? = null,
        val cost: Int = 0,
        val previousState: SearchState? = null
    ) : Comparable<SearchState> {
        override fun compareTo(other: SearchState): Int = cost.compareTo(other.cost)
    }
}

