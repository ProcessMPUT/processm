package processm.services.api

import processm.core.helpers.getPropertyIgnoreCase

/**
 * The system's configuration exposed using API.
 */
class Config(
    val loginMessage: String = getPropertyIgnoreCase("processm.webui.loginMessage") ?: "",
    val demoMode: Boolean = getPropertyIgnoreCase("processm.demoMode")?.toBoolean() ?: false
)
