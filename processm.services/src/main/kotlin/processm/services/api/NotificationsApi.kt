package processm.services.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.routing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import org.koin.ktor.ext.inject
import processm.logging.logger
import processm.services.helpers.eventStream
import processm.services.logic.ComponentUpdateEventPayload
import processm.services.logic.NotificationService

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("FunctionName")
@KtorExperimentalLocationsAPI
fun Route.NotificationsApi() {
    val notificationService by inject<NotificationService>()
    val logger = logger()

    authenticate {
        get<Paths.Notifications> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!
            val channel = Channel<ComponentUpdateEventPayload>(Channel.CONFLATED)
            try {
                notificationService.subscribe(principal.userId, channel)
                call.eventStream(this) {
                    while (!channel.isClosedForReceive) {
                        writeEvent(channel.receive())
                    }
                }
            } catch (e: Exception) {
                logger.error("Exception in the workspace notification handler", e)
            } finally {
                notificationService.unsubscribe(principal.userId, channel)
                channel.close()
            }
        }
    }
}
