package processm.services.logic

import jakarta.jms.MapMessage
import jakarta.jms.Message
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import processm.core.esb.AbstractJMSListener
import processm.core.helpers.toUUID
import processm.dbmodels.models.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * A service to receive and distribute notifications about data changes in workflow components.
 * The service receives [DATA_CHANGE] events in the [WORKSPACE_COMPONENTS_TOPIC] in AMQP and distribute them
 * via [Channel]s to interested parties.
 */
class WorkspaceNotificationService {

    private val clients = ConcurrentHashMap<UUID, ConcurrentLinkedDeque<Channel<UUID>>>()

    /**
     * Subscribe the given [channel] to receive notifications about data changes in the components in the workspace
     * identified by [workspaceId]. [WorkspaceComponent.id] is posted to the channel every time an AMQP event in the topic
     * [WORKSPACE_COMPONENTS_TOPIC] with the [WORKSPACE_COMPONENT_EVENT] = [DATA_CHANGE] is received.
     *
     * If sending a notification fails, the channel is closed and unsubscribed.
     */
    fun subscribe(workspaceId: UUID, channel: Channel<UUID>) {
        synchronized(clients) {
            if (clients.isEmpty())
                listener.listen()
            clients.computeIfAbsent(workspaceId) { ConcurrentLinkedDeque() }.addLast(channel)
        }
    }

    /**
     * Unsubscribe the [channel] from receiving notifications for [workspaceId]. The channel is not closed.
     */
    fun unsubscribe(workspaceId: UUID, channel: Channel<UUID>) = unsubscribe(workspaceId, setOf(channel))

    fun unsubscribe(workspaceId: UUID, channel: Set<Channel<UUID>>) {
        synchronized(clients) {
            val queue = clients[workspaceId]
            if (queue !== null && queue.removeAll(channel) && queue.isEmpty()) {
                clients.remove(workspaceId)
            }
            if (clients.isEmpty())
                listener.close()
        }
    }

    private val listener = Listener()

    private inner class Listener :
        AbstractJMSListener(WORKSPACE_COMPONENTS_TOPIC, null, "WorkspaceNotificationService") {

        override fun onMessage(msg: Message?) {
            if (msg !is MapMessage) return
            val event = msg.getString(WORKSPACE_COMPONENT_EVENT)
            if (event != DATA_CHANGE) return
            val componentId = checkNotNull(msg.getString(WORKSPACE_COMPONENT_ID).toUUID())
            val workspaceId = checkNotNull(msg.getString(WORKSPACE_ID).toUUID())
            val failed = HashSet<Channel<UUID>>()
            runBlocking(CoroutineName("WorkspaceServices#Listener#onMessage")) {
                clients[workspaceId]?.forEach { channel ->
                    val result = channel.trySend(componentId)
                    if (!result.isSuccess) {
                        channel.close()
                        failed.add(channel)
                    }
                }
            }
            if (failed.isNotEmpty()) {
                unsubscribe(workspaceId, failed)
            }
        }
    }
}