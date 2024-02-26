package processm.core.models.petrinet

import kotlinx.serialization.Serializable
import processm.core.models.commons.Activity
import processm.helpers.SerializableUUID
import java.util.*


/**
 * A transition in a Petri net.
 *
 * @property inPlaces The collection of the preceding places.
 * @property outPlaces The collection of the succeeding places.
 */
@Serializable
open class Transition(
    override val name: String,
    val inPlaces: Collection<Place> = emptyList(),
    val outPlaces: Collection<Place> = emptyList(),
    override val isSilent: Boolean = false,
    /**
     * A unique identifier to maintain identity of the object, e.g., between the frontend and the backend.
     */
    val id: SerializableUUID = UUID.randomUUID()
) : Activity {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Transition) return false

        if (name != other.name) return false
        if (inPlaces != other.inPlaces) return false
        if (outPlaces != other.outPlaces) return false
        if (isSilent != other.isSilent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + inPlaces.hashCode()
        result = 31 * result + outPlaces.hashCode()
        result = 31 * result + isSilent.hashCode()
        return result
    }

    fun copy(
        name: String = this.name,
        inPlaces: Collection<Place> = this.inPlaces,
        outPlaces: Collection<Place> = this.outPlaces,
        isSilent: Boolean = this.isSilent
    ): Transition = Transition(name, inPlaces, outPlaces, isSilent)

    override fun toString(): String {
        return if (isSilent) return "τ" else name
    }
}
