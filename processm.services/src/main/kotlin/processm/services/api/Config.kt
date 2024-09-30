package processm.services.api

import kotlinx.serialization.Serializable
import processm.core.Brand
import processm.helpers.getPropertyIgnoreCase

/**
 * The system's configuration exposed using API.
 */
@Serializable
class Config(
    val brand: String = Brand.name,
    val version: String = Brand.version,
    val loginMessage: String = getPropertyIgnoreCase("processm.webui.loginMessage") ?: "",
    val demoMode: Boolean = getPropertyIgnoreCase("processm.demoMode")?.toBoolean() ?: false,
    val maxUploadSize: Long = getPropertyIgnoreCase("processm.logs.limit.maxUploadSize")?.toLongOrNull() ?: (5 * 1024 * 1024L),
    val gaTag: String? = getPropertyIgnoreCase("processm.webui.gaTag")
)
