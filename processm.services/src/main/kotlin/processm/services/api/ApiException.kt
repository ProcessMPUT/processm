package processm.services.api

import processm.helpers.LocalizedException
import processm.services.helpers.ExceptionReason

/**
 * Represents failure during processing of API request.
 *
 * @property reason the cause of the exception, to be translated and sent to the client, informing about the reason of failed processing.
 * @property arguments the arguments that may be necessary to correctly format the message
 * @property message message passed to parent [Exception]. If not provided, [reason] is used.
 */
class ApiException(
    reason: ExceptionReason,
    arguments: Array<Any?> = emptyArray(),
    message: String? = null
) : LocalizedException(reason, arguments, message)

