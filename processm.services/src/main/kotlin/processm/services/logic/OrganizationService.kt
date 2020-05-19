package processm.services.logic

import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.DBConnectionPool
import processm.services.models.Organization
import java.util.*

class OrganizationService {

    fun getOrganizationMembers(organizationId: UUID) = transaction(DBConnectionPool.database) {
        // this returns only users explicitly assigned to the organization
        val organization = getOrganizationDao(organizationId)

        organization.userRoles.map { it.toDto() }
    }

    fun getOrganizationGroups(organizationId: UUID) = transaction(DBConnectionPool.database) {
        val organization = getOrganizationDao(organizationId)

        organization.userGroups.map { it.toDto() }
    }

    private fun getOrganizationDao(organizationId: UUID) = transaction(DBConnectionPool.database) {
        Organization.findById(organizationId) ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "Specified organization does not exist"
        )
    }
}