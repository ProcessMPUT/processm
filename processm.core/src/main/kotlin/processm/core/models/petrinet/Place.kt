package processm.core.models.petrinet

import kotlinx.serialization.Serializable
import processm.helpers.SerializableUUID
import java.util.*

/**
 * A place in a Petri net.
 */
@Serializable
open class Place(
    /**
     * A unique identifier to maintain identity of a [Place] during serialization
     */
    val id: SerializableUUID = UUID.randomUUID()
) {
    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = other is Place && id == other.id
    override fun toString(): String = "Place[$id]"
}
