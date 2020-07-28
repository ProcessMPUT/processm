package processm.services.logic

import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.DBConnectionPool
import processm.services.models.Organization
import processm.services.models.Organizations
import processm.services.models.UserGroup
import java.util.*

class OrganizationService {

    fun getOrganizationMembers(organizationId: UUID) = transaction(DBConnectionPool.database) {
        // this returns only users explicitly assigned to the organization
        val organization = getOrganizationDao(organizationId)

        organization.userRoles.map { it.toDto() }
    }

    fun getOrganizationGroups(organizationId: UUID) = transaction(DBConnectionPool.database) {
        val organization = getOrganizationDao(organizationId)

        return@transaction listOf(organization.sharedGroup.toDto())
    }

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