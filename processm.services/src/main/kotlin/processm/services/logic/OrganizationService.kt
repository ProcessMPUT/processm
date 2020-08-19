package processm.services.logic

import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.DBConnectionPool
import processm.services.models.Organization
import processm.services.models.Organizations
import processm.services.models.UserGroup
import java.util.*

class OrganizationService {

    /**
     * Returns all users explicitly assigned to the specified [organizationId].
     * Throws [ValidationException] if the specified [organizationId] doesn't exist.
     */
    fun getOrganizationMembers(organizationId: UUID) = transaction(DBConnectionPool.database) {
        val organization = getOrganizationDao(organizationId)

        organization.userRoles.map { it.toDto() }
    }

    /**
     * Returns all user groups explicitly assigned to the specified [organizationId].
     * Throws [ValidationException] if the specified [organizationId] doesn't exist.
     */
    fun getOrganizationGroups(organizationId: UUID) = transaction(DBConnectionPool.database) {
        val organization = getOrganizationDao(organizationId)

        return@transaction listOf(organization.sharedGroup.toDto())
    }

    /**
     * Returns organization by the specified [sharedGroupId].
     * Throws [ValidationException] if the organization doesn't exist.
     */
    fun getOrganizationBySharedGroupId(sharedGroupId: UUID) = transaction(DBConnectionPool.database) {
        val organization = Organizations.select {Organizations.sharedGroupId eq sharedGroupId }.firstOrNull()
           ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The specified shared group id is not assigned to any organization")

        return@transaction Organization.wrapRow(organization).toDto()
    }

    private fun getOrganizationDao(organizationId: UUID) = transaction(DBConnectionPool.database) {
        Organization.findById(organizationId) ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "The specified organization does not exist"
        )
    }
}