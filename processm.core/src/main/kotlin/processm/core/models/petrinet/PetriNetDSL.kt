package processm.core.models.petrinet

/**
 * A domain specific language for defininig PetriNets. For convenience it identifies transitions by their names and
 * assumes that the same name always denotes the same transition. The user defines only places and the definitions
 * of transitions are constructed from them. The order of places remains unchanged and the first place is assumed to have the initial marking,
 * while the last place is assumed to have the final marking.
 *
 * The following syntax is used:
 *
 * ```
 * PetriNet = Place+
 * Place = "P" IncomingTransitions? OutgoingTransitions?
 * IncomingTransitions = "tin" Transitions
 * OutgoingTransitions = "tout" Transitions
 * Transitions = Transition | (Transition "*" Transitions)
 * Transition = NormalTransition | SilentTransition
 * NormalTransition = A string not starting with an underscore
 * SilentTransition = A string starting with an underscore
 * ```
 *
 * Example:
 * ```
 * petrinet {
 * P tout "a" // a place with no incoming transitions and an outgoing transition "a"
 * P tin "a" * "f" tout "b" * "c" // a place with two incoming transitions ("a" and "f") and two outgoing transitions ("b" and "c")
 * P tin "a" * "f" tout "d"
 * P tin "b" * "c" tout "e"
 * P tin "d" tout "e"
 * P tin "e" tout "g" * "h" * "f"
 * P tin "g" * "h"
 * }
 * ```
 * This example defines Petri net M1 from "Replaying History on Process Models for Conformance Checking and Performance Analysis" (DOI 10.1002/widm.1045).
 * Which happens to be the same as Fig 3.2 from the PM book.
 *
 * By construction of the DSL, each transition must have an unique name. If this is undesirable, set [namePostprocessor]
 * to a function which, given a transition name used in the DSL, returns a name to be used in the final model.
 */
class PetriNetDSL {
    class PlaceDescriptor {
        internal val tout = ArrayList<String>()
        internal val tin = ArrayList<String>()

        infix fun tout(t: String): PlaceDescriptor {
            tout.add(t)
            return this
        }

        infix fun tout(t: List<String>): PlaceDescriptor {
            tout.addAll(t)
            return this
        }

        infix fun tin(t: String): PlaceDescriptor {
            tin.add(t)
            return this
        }

        infix fun tin(t: List<String>): PlaceDescriptor {
            tin.addAll(t)
            return this
        }
    }

    operator fun String.times(other: String): ArrayList<String> = arrayListOf(this, other)


    operator fun ArrayList<String>.times(other: String): ArrayList<String> {
        this.add(other)
        return this
    }

    class PCls(val dsl: PetriNetDSL) {
        infix fun tin(other: String) = dsl.place().tin(other)
        infix fun tin(other: ArrayList<String>) = dsl.place().tin(other)
        infix fun tout(other: String) = dsl.place().tout(other)
        infix fun tout(other: ArrayList<String>) = dsl.place().tout(other)
    }

    val P = PCls(this)

    /**
     * Transforms transitions' names used in the DSL to names used in the final model. If `null` the names are intact.
     */
    var namePostprocessor: ((String) -> String)? = null

    private val pds = ArrayList<PlaceDescriptor>()

    fun place() = PlaceDescriptor().also { pds.add(it) }

    fun result(): PetriNet {
        val places = pds.map { Place() }
        val tmap = HashMap<String, Pair<ArrayList<Place>, ArrayList<Place>>>()
        for ((p, pd) in places zip pds) {
            for (tname in pd.tin)
                tmap.computeIfAbsent(tname) { ArrayList<Place>() to ArrayList() }.first.add(p)
            for (tname in pd.tout)
                tmap.computeIfAbsent(tname) { ArrayList<Place>() to ArrayList() }.second.add(p)
        }
        val transitions = tmap.entries.map { (name, places) ->
            Transition(
                namePostprocessor?.let { it(name) } ?: name,
                places.second,
                places.first,
                isSilent = name[0] == '_'
            )
        }
        return PetriNet(places, transitions)
    }
}

fun petrinet(init: PetriNetDSL.() -> Unit): PetriNet {
    val dsl = PetriNetDSL()
    dsl.init()
    return dsl.result()
}