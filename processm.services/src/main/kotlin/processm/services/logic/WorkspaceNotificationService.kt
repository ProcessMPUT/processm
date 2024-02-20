package processm.services.logic

import jakarta.jms.MapMessage
import jakarta.jms.Message
import kotlinx.coroutines.channels.Channel
import processm.core.esb.AbstractJMSListener
import processm.core.helpers.toUUID
import processm.core.logging.loggedScope
import processm.dbmodels.models.*
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A service to receive and distribute notifications about data changes in workflow components.
 * The service receives [DATA_CHANGE] events in the [WORKSPACE_COMPONENTS_TOPIC] in AMQP and distribute them
 * via [Channel]s to interested parties.
 */
class WorkspaceNotificationService {

    private val clients = HashMap<UUID, ArrayDeque<Channel<UUID>>>()
    private val lock = ReentrantReadWriteLock()

    /**
     * Subscribe the given [channel] to receive notifications about data changes in the components in the workspace
     * identified by [workspaceId]. [WorkspaceComponent.id] is posted to the channel every time an AMQP event in the topic
     * [WORKSPACE_COMPONENTS_TOPIC] with the [WORKSPACE_COMPONENT_EVENT] = [DATA_CHANGE] is received.
     *
     * If sending a notification fails, the channel is closed and unsubscribed.
     */
    fun subscribe(workspaceId: UUID, channel: Channel<UUID>) = loggedScope { logger ->
        lock.write {
            val isEmpty = clients.isEmpty()
            clients.computeIfAbsent(workspaceId) { ArrayDeque<Channel<UUID>>() }.addLast(channel)
            if (isEmpty)
                listener.listen()
        }
    }

    /**
     * Unsubscribe the [channel] from receiving notifications for [workspaceId]. The channel is not closed.
     */
    fun unsubscribe(workspaceId: UUID, channel: Channel<UUID>) = unsubscribe(workspaceId, listOf(channel))

    fun unsubscribe(workspaceId: UUID, channel: Collection<Channel<UUID>>) = loggedScope { logger ->
        lock.write {
            clients.computeIfPresent(workspaceId) { _, queue ->
                queue.removeAll(channel)
                channel.forEach { it.close() }
                if (queue.isEmpty()) null
                else queue
            }

            if (clients.isEmpty())
                listener.close()
        }
    }

    private val listener = Listener()

    private inner class Listener :
        AbstractJMSListener(
            WORKSPACE_COMPONENTS_TOPIC,
            "$WORKSPACE_COMPONENT_EVENT = '$DATA_CHANGE'",
            "WorkspaceNotificationService",
            false
        ) {

        override fun onMessage(msg: Message?) = loggedScope { logger ->
            logger.error("onMessage $msg")
            if (msg !is MapMessage) return
            val componentId = checkNotNull(msg.getString(WORKSPACE_COMPONENT_ID).toUUID())
            val workspaceId = checkNotNull(msg.getString(WORKSPACE_ID).toUUID())
            val failed = ArrayList<Channel<UUID>>()
            lock.read {
                clients[workspaceId]?.forEach { channel ->
                    val result = channel.trySend(componentId)
                    if (!result.isSuccess) {
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
