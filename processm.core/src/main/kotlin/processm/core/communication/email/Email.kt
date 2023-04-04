package processm.core.communication.email

import jakarta.mail.Message
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.io.ByteArrayOutputStream
import java.util.*

object Emails : UUIDTable("emails") {
    val recipients = text("recipients")
    val body = binary("body")
    val sent = timestamp("sent").nullable()
}

class Email(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Email>(Emails)

    /**
     * [EMAIL_ADDRESS_SEPARATOR]-separated recpients of the e-mail
     */
    var recipients by Emails.recipients

    /**
     * Encoded body of the email, in a format suitable for passing directly to a mail server
     */
    var body by Emails.body

    /**
     * The timestamp the email was delivered to the mail server or `null` if it hasn't been delivered yet
     */
    var sent by Emails.sent
}

/**
 * Populate the DB fields from [email]
 */
fun Email.fromMessage(email: Message) {
    recipients = email.allRecipients.joinToString(separator = EMAIL_ADDRESS_SEPARATOR) {
        val emailAddress = it.toString()
        require(EMAIL_ADDRESS_SEPARATOR !in emailAddress)
        emailAddress
    }
    ByteArrayOutputStream().use { stream ->
        email.writeTo(stream)
        body = stream.toByteArray()
    }
}