package processm.services.logic

import jakarta.jms.MapMessage
import jakarta.jms.Message
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import processm.core.esb.AbstractJMSListener
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import processm.helpers.SerializableUUID
import processm.helpers.toUUID
import processm.logging.loggedScope
import processm.services.helpers.ServerSentEvent
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@ServerSentEvent("update")
@Serializable
data class ComponentUpdateEventPayload(
    val componentId: SerializableUUID,
    val workspaceId: SerializableUUID,
    val changeType: String,
    val workspaceName: String,
    val componentName: String
)

/**
 * A service to receive and distribute notifications about data changes in workflow components.
 * The service receives [WorkspaceComponentEventType.DataChange] events in the [WORKSPACE_COMPONENTS_TOPIC] in AMQP and distribute them
 * via [Channel]s to interested parties.
 */
class NotificationService(private val aclService: ACLService) {

    private val clients = HashMap<UUID, ArrayDeque<Channel<ComponentUpdateEventPayload>>>()
    private val lock = ReentrantReadWriteLock()

    /**
     * Subscribe the given [channel] to receive notifications about changes in the components the user identified by [userId]
     * has access to. An object of the type [ComponentUpdateEventPayload] is posted to the channel every
     * time an AMQP event in the topic [WORKSPACE_COMPONENTS_TOPIC] with the [WORKSPACE_COMPONENT_EVENT] =
     * [WorkspaceComponentEventType.DataChange] is received.
     *
     * If sending a notification fails, the channel is closed and unsubscribed.
     */
    fun subscribe(userId: UUID, channel: Channel<ComponentUpdateEventPayload>) = loggedScope { logger ->
        lock.write {
            val isEmpty = clients.isEmpty()
            clients.computeIfAbsent(userId) { ArrayDeque<Channel<ComponentUpdateEventPayload>>() }.addLast(channel)
            if (isEmpty)
                listener.listen()
        }
    }

    /**
     * Unsubscribe the [channel] from receiving notifications for [userId]. The channel is closed.
     */
    fun unsubscribe(userId: UUID, channel: Channel<ComponentUpdateEventPayload>) =
        unsubscribe(listOf(userId to channel))

    /**
     * Each pair in [channels] should correspond to a userId and a channel receiving notifications for that user, registered
     * via [subscribe]. They are all unsubscribed, the channels are closed, and if possible, auxiliary data structures are removed.
     */
    fun unsubscribe(channels: Collection<Pair<UUID, Channel<ComponentUpdateEventPayload>>>) = loggedScope { logger ->
        lock.write {
            channels.forEach { (userId, channel) ->
                clients.computeIfPresent(userId) { _, queue ->
                    queue.remove(channel)
                    channel.close()
                    if (queue.isEmpty()) null
                    else queue
                }
            }

            if (clients.isEmpty())
                listener.close()
        }
    }

    private val listener = Listener()

    private inner class Listener :
        AbstractJMSListener(
            WORKSPACE_COMPONENTS_TOPIC,
            "$WORKSPACE_COMPONENT_EVENT = '${WorkspaceComponentEventType.DataChange}'",
            "NotificationService",
            false
        ) {

        override fun onMessage(msg: Message?) = loggedScope { logger ->
            logger.debug("onMessage {}", msg)
            if (msg !is MapMessage) return
            val componentId = checkNotNull(msg.getString(WORKSPACE_COMPONENT_ID).toUUID())
            val workspaceId = checkNotNull(msg.getString(WORKSPACE_ID).toUUID())
            val changeType = msg.getStringProperty(WORKSPACE_COMPONENT_EVENT_DATA) ?: ""
            val notification = transactionMain {
                val workspaceName = Workspace[workspaceId].name
                val componentName = WorkspaceComponent[componentId].name
                ComponentUpdateEventPayload(componentId, workspaceId, changeType, workspaceName, componentName)
            }

            val failed = ArrayList<Pair<UUID, Channel<ComponentUpdateEventPayload>>>()
            val usersToNotify = aclService.usersWithAccess(
                aclService.getURN(Workspaces, workspaceId),
                userIDs = lock.read { clients.keys })

            lock.read {
                logger.debug("Event in workspace {}, notifying {}", workspaceId, usersToNotify)
                usersToNotify.forEach { userId ->
                    clients[userId]?.forEach { channel ->
                        val result = channel.trySend(notification)
                        if (!result.isSuccess) {
                            failed.add(userId to channel)
                        }
                    }
                }
            }
            if (failed.isNotEmpty()) {
                unsubscribe(failed)
            }
        }
    }
}
