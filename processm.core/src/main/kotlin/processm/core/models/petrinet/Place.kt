package processm.core.models.petrinet

import java.util.UUID

import kotlinx.serialization.Serializable

/**
 * A place in a Petri net.
 */
@Serializable
open class Place(
    // TODO: Verify if this should be set here
    val id: String = UUID.randomUUID().toString()
)
