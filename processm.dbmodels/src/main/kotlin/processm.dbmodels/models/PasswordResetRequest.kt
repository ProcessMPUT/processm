package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import processm.core.communication.email.Email
import processm.core.communication.email.Emails
import processm.dbmodels.models.Organization.Companion.referrersOn
import java.util.*

object PasswordResetRequests : UUIDTable("password_reset_requests") {
    val userId = reference("user_id", Users)
    val notBefore = timestamp("not_before")
    val notAfter = timestamp("not_after")
    val email = reference("email", Emails).nullable()
    val linkClicked = timestamp("link_clicked").nullable()
}

/**
 * id is a token to be presented to the API to reset the password
 */
class PasswordResetRequest(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PasswordResetRequest>(PasswordResetRequests)

    /**
     * The token's owner
     */
    var user by User referencedOn PasswordResetRequests.userId

    /**
     * The earliest moment the token would be accepted
     */
    var notBefore by PasswordResetRequests.notBefore

    /**
     * The latest moment the token would be accepted
     */
    var notAfter by PasswordResetRequests.notAfter

    /**
     * The e-mail message delivering the token. The token is valid only if the corresponding email has been created and send, i.e., the user had an opportunity to see the token
     * The reference is optional to allow for removing old emails from the database
     */
    var email by Email optionalReferencedOn PasswordResetRequests.email

    /**
     * The timestamp for the password change using this token or `null` if the token was not used so far
     */
    var linkClicked by PasswordResetRequests.linkClicked
}