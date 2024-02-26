package processm.core.communication.email

import jakarta.jms.MapMessage
import jakarta.jms.Message
import org.jetbrains.exposed.sql.select
import org.quartz.*
import processm.core.esb.AbstractJobService
import processm.core.esb.ServiceJob
import processm.core.persistence.connection.transactionMain
import processm.helpers.getPropertyIgnoreCase
import processm.helpers.toUUID
import java.time.Instant
import java.util.*

const val EMAIL_TOPIC = "email"
const val EMAIL_ID = "id"

/**
 * A separator to separate different recipients of a single email in the DB.
 * I think in principle RFC 5322 allows for \n in an email address. I don't care, since nobody does it anyway.
 */
const val EMAIL_ADDRESS_SEPARATOR = "\n"


internal class SendEmailJob : ServiceJob {

    override fun execute(context: JobExecutionContext) {
        val id = requireNotNull(context.jobDetail.key.name?.toUUID())
        transactionMain {
            val email = Email.findById(id)
            if (email !== null) {
                if (getPropertyIgnoreCase("processm.email.useSMTP")?.toBoolean() == true)
                    TODO("Sending via SMTP is currently not implemented")
                else
                    Sendmail().send(email)
                email.sent = Instant.now()
            }
        }
    }
}

/**
 * A service handling sending emails. The emails themselves are stored in the [Emails] table, and the service
 * listens to messages on the topic [EMAIL_TOPIC] to get notified once a new email is available for sending
 */
class EmailService : AbstractJobService(QUARTZ_CONFIG, EMAIL_TOPIC, null) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-email.properties"
    }

    override val name: String
        get() = "e-mail"

    override fun loadJobs(): List<Pair<JobDetail, Trigger>> {
        val emails = transactionMain {
            Emails.select { Emails.sent.isNull() }.map { it[Emails.id].value }
        }
        return emails.map { createJob(it) }
    }

    override fun messageToJobs(message: Message): List<Pair<JobDetail, Trigger>> {
        require(message is MapMessage) { "Unrecognized message $message." }
        val id = UUID.fromString(message.getString(EMAIL_ID))
        return listOf(createJob(id))
    }

    private fun createJob(id: UUID): Pair<JobDetail, Trigger> {
        val job = JobBuilder
            .newJob(SendEmailJob::class.java)
            .withIdentity(id.toString())
            .build()
        val trigger = TriggerBuilder
            .newTrigger()
            .withIdentity(id.toString())
            .startNow()
            .build()

        return job to trigger
    }
}
