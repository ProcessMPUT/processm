package processm.core.models.petrinet

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

internal const val SEPARATOR = ","

internal object PetriNets : UUIDTable("petrinets")

internal object Transitions : UUIDTable("petrinets_transitions") {
    val petrinet = reference("petrinet", PetriNets)
    val name = text("name")
    val isSilent = bool("is_silent")

    /**
     * Comma-separated identifiers of places
     *
     * This is ugly, but Exposed currently doesn't support arrays (see https://github.com/JetBrains/Exposed/issues/150)
     * and creating a separate table seems wasteful
     */
    val inPlaces = text("in_places")
    val outPlaces = text("out_places")
}

internal object Places : UUIDTable("petrinets_places") {
    val petrinet = reference("petrinet", PetriNets)
    val initialMarking = integer("initial_marking").nullable()
    val finalMarking = integer("final_marking").nullable()
}

internal class PlaceModel(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PlaceModel>(Places)

    var petrinet by PetriNetModel referencedOn Places.petrinet
    var initialMarking by Places.initialMarking
    var finalMarking by Places.finalMarking
}

internal class TransitionModel(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TransitionModel>(Transitions)

    var petrinet by PetriNetModel referencedOn Transitions.petrinet
    var name by Transitions.name
    var isSilent by Transitions.isSilent
    var inPlaces by Transitions.inPlaces
    var outPlaces by Transitions.outPlaces
}

internal class PetriNetModel(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PetriNetModel>(PetriNets)

    val transitions by TransitionModel referrersOn Transitions.petrinet
    val places by PlaceModel referrersOn Places.petrinet
}

object DBSerializer {
    /**
     * Inserts the [petriNet] model into the database [database] and returns the DB identifier of the model
     *
     * Includes places, transitions and markings
     */
    fun insert(database: Database, petriNet: PetriNet): UUID =
        transaction(database) {
            addLogger(Slf4jSqlDebugLogger)
            val petriNetModel = PetriNetModel.new(UUID.randomUUID()) {}
            for (place in petriNet.places) {
                PlaceModel.new(UUID.fromString(place.id)) {
                    petrinet = petriNetModel
                    initialMarking = petriNet.initialMarking[place]
                    finalMarking = petriNet.finalMarking[place]
                }
            }
            for (transition in petriNet.transitions) {
                TransitionModel.new {
                    petrinet = petriNetModel
                    name = transition.name
                    isSilent = transition.isSilent
                    inPlaces = transition.inPlaces.joinToString(separator = SEPARATOR) { it.id }
                    outPlaces = transition.outPlaces.joinToString(separator = SEPARATOR) { it.id }
                }
            }
            return@transaction petriNetModel.id.value
        }

    /**
     * Returns [PetriNet] present in the [database] under the id [modelId]
     *
     * @throws NoSuchElementException If there's no Petri net in the db with the given id
     */
    fun fetch(database: Database, modelId: UUID): PetriNet = transaction(database) {
        addLogger(Slf4jSqlDebugLogger)
        val petriNetModel = PetriNetModel.findById(modelId) ?: throw NoSuchElementException()
        val places = HashMap<String, Place>()
        val initialMarking = Marking()
        val finalMarking = Marking()
        for (placeModel in petriNetModel.places) {
            val id = placeModel.id.value.toString()
            val place = Place(id)
            places[id] = place
            placeModel.initialMarking?.let { initialMarking[place] = it }
            placeModel.finalMarking?.let { finalMarking[place] = it }
        }
        val transitions = ArrayList<Transition>()
        for (transitionModel in petriNetModel.transitions) {
            val inPlaces = transitionModel.inPlaces.split(SEPARATOR).map { places.getValue(it) }
            val outPlaces = transitionModel.outPlaces.split(SEPARATOR).map { places.getValue(it) }
            transitions.add(
                Transition(
                    transitionModel.name,
                    inPlaces = inPlaces,
                    outPlaces = outPlaces,
                    isSilent = transitionModel.isSilent
                )
            )
        }
        return@transaction PetriNet(places.values.toList(), transitions, initialMarking, finalMarking)
    }

    /**
     * Deletes [PetriNet] present in the [database] under the id [modelId] along with all its transitions and places
     *
     * @throws NoSuchElementException If there's no Petri net in the db with the given id
     */
    fun delete(database: Database, modelId: UUID): Unit = transaction(database) {
        addLogger(Slf4jSqlDebugLogger)
        val model = PetriNetModel.findById(modelId) ?: throw NoSuchElementException()
        TransitionModel.find { Transitions.petrinet eq modelId }.forEach(TransitionModel::delete)
        PlaceModel.find { Places.petrinet eq modelId }.forEach(PlaceModel::delete)
        model.delete()
    }
}