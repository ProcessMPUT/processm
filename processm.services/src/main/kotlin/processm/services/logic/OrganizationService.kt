package processm.services.logic

import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.Organization
import processm.dbmodels.models.Organizations
import java.util.*

class OrganizationService {

    /**
     * Returns all users explicitly assigned to the specified [organizationId].
     * Throws [ValidationException] if the specified [organizationId] doesn't exist.
     */
    fun getOrganizationMembers(organizationId: UUID) = transaction(DBCache.getMainDBPool().database) {
        // this returns only users explicitly assigned to the organization
        val organization = getOrganizationDao(organizationId)

        organization.userRoles.map { it.toDto() }
    }

    /**
     * Returns all user groups explicitly assigned to the specified [organizationId].
     * Throws [ValidationException] if the specified [organizationId] doesn't exist.
     */
    fun getOrganizationGroups(organizationId: UUID) = transaction(DBCache.getMainDBPool().database) {
        val organization = getOrganizationDao(organizationId)

        return@transaction listOf(organization.sharedGroup.toDto())
    }

    /**
     * Returns organization by the specified [sharedGroupId].
     * Throws [ValidationException] if the organization doesn't exist.
     */
    fun getOrganizationBySharedGroupId(sharedGroupId: UUID) = transaction(DBCache.getMainDBPool().database) {
        val organization = Organizations.select { Organizations.sharedGroupId eq sharedGroupId }.firstOrNull()
            ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The specified shared group id is not assigned to any organization"
            )

        return@transaction Organization.wrapRow(organization).toDto()
    }

    private fun getOrganizationDao(organizationId: UUID) = transaction(DBCache.getMainDBPool().database) {
        Organization.findById(organizationId) ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "The specified organization does not exist"
        )
    }
}