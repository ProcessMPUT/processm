package processm.core.models.petrinet

import java.util.UUID

import kotlinx.serialization.Serializable
import java.util.*

/**
 * A place in a Petri net.
 */
@Serializable
open class Place(
    /**
     * A unique identifier to maintain identity of a [Place] during serialization
     */
    val id: String = UUID.randomUUID().toString()
) {
    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = other is Place && id == other.id
    override fun toString(): String = "Place[$id]"
}
