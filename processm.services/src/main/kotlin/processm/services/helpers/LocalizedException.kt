package processm.services.helpers

import io.ktor.http.*
import processm.helpers.AbstractLocalizedException
import processm.logging.logger
import java.util.*


/**
 * An exception supporting localization according to the remote user's locale
 *
 * @property reason The reason for the exception
 * @property arguments Arguments for the description of the exception (a format string) retrieved from resources
 * @property message message passed to parent [Exception]. If not provided, `reason.toString()` is used.
 */
open class LocalizedException(
    val reason: ExceptionReason,
    val arguments: Array<out Any?> = emptyArray(),
    message: String? = null
) : AbstractLocalizedException(message ?: reason.toString()) {

    override fun localizedMessage(locale: Locale): String = try {
        val formatString = getFormatString(locale, reason.toString())
        String.format(locale, formatString, *arguments)
    } catch (e: Exception) {
        logger().error("An exception was thrown while preparing localized exception", e)
        message ?: reason.toString()
    }
}

enum class ExceptionReason(val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) {
    UnspecifiedReason,
    NoFieldInToken,
    NotAMemberOfOrganization(HttpStatusCode.Forbidden),
    InsufficientPermissionInOrganization(HttpStatusCode.Forbidden),
    UnparsableData,
    LastACECannotBeDowngraded(HttpStatusCode.UnprocessableEntity),
    ACENotFound(HttpStatusCode.NotFound),
    LastACECannotBeRemoved(HttpStatusCode.UnprocessableEntity),
    ACLCannotBeModified(HttpStatusCode.Forbidden),
    ACLCannotBeRead(HttpStatusCode.Forbidden),
    InvalidUsernameOrPassword(HttpStatusCode.Unauthorized),

    @Deprecated("In principle this error should not happen. It is left just in case.")
    PQLError,

    UnexpectedRequestParameter,
    InvalidFile,
    UnsupportedContentType,
    ConnectorConfigurationRequired,
    ConnectorNameRequired,
    DataStoreNameRequired,
    ConnectorReferenceRequired,
    ETLProcessNameRequired,
    EmptyETLConfigurationNotSupported,
    ETLProcessTypeNotSupported,
    ActivationStatusRequired,
    NotFound(HttpStatusCode.NotFound),
    TokenExpired(HttpStatusCode.Unauthorized),
    CredentialsOrTokenRequired,
    WorkspaceNameRequired,
    ConnectionTestFailed,
    InvalidTokenFormat(HttpStatusCode.Unauthorized),
    ParentOrganizationAlreadySet,
    InvalidUserID,
    OrganizationNotFound(HttpStatusCode.NotFound),
    OrganizationAlreadyTopLevel(HttpStatusCode.UnprocessableEntity),
    UserNotFound(HttpStatusCode.NotFound),
    GroupNotFound(HttpStatusCode.NotFound),
    UserNotFoundInOrganization(HttpStatusCode.NotFound),
    DataStoreRequired,
    ComponentTypeRequired,
    QueryRequired,
    SharedGroupNotAssigned(HttpStatusCode.NotFound),
    WorkspaceNotFound(HttpStatusCode.NotFound),
    WorkspaceComponentNotFound(HttpStatusCode.NotFound),
    SoleOwner(HttpStatusCode.UnprocessableEntity),
    BlankName,
    InvalidGroupSpecification,
    CannotDetachFromSharedGroup,
    CannotDetachFromImplicitGroup,
    UserOrGroupNotFound(HttpStatusCode.NotFound),
    OrganizationCannotBeItsOwnChild,
    AlreadyDescendant,
    UserAlreadyInOrganization(HttpStatusCode.Conflict),
    NotADirectSuborganization,
    CannotChangeRole(HttpStatusCode.UnprocessableEntity),
    CannotDelete(HttpStatusCode.UnprocessableEntity),
    InvalidLocale,
    CannotChangeLocale,
    InvalidEmail,
    PasswordTooWeak,
    UserAlreadyExists(HttpStatusCode.Conflict),
    InsufficientPermissionToURN(HttpStatusCode.Forbidden),
    DataStoreNotFound(HttpStatusCode.NotFound),
    ETLProcessNotFound(HttpStatusCode.NotFound)
}

