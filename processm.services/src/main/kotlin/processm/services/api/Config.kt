package processm.services.api

import kotlinx.serialization.Serializable
import processm.core.helpers.getPropertyIgnoreCase

/**
 * The system's configuration exposed using API.
 */
@Serializable
class Config(
    val loginMessage: String = getPropertyIgnoreCase("processm.webui.loginMessage") ?: "",
    val demoMode: Boolean = getPropertyIgnoreCase("processm.demoMode")?.toBoolean() ?: false
)
