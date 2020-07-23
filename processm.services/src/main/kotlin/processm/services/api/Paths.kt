package processm.services.api

import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location

object Paths {
    /**
     * Get or remove specified group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}") class Group(val groupId: java.util.UUID)

    /**
     * Get members of the specified group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/members") class GroupMembers(val groupId: java.util.UUID)

    /**
     * Get groups belonging to the current user&#39;s organization
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups") class Groups()

    /**
     * Get subgroups of the specified group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/subgroups") class Subgroups(val groupId: java.util.UUID)

    /**
     * Remove member from the specified group
     *
     * @param groupId Group ID
     * @param userId User ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/members/{userId}") class GroupMember(val groupId: java.util.UUID, val userId: java.util.UUID)

    /**
     * Remove speciified subgroup
     *
     * @param groupId Group ID
     * @param subgroupId Subgroup ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/subgroups/{subgroupId}") class Subgroup(val groupId: java.util.UUID, val subgroupId: java.util.UUID)

    /**
     * Get or remove specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}") class Organization(val organizationId: java.util.UUID)

    /**
     * Get groups associated with the specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/groups") class OrganizationGroups(val organizationId: java.util.UUID)

    /**
     * Get members of the specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/members") class OrganizationMembers(val organizationId: java.util.UUID)

    /**
     * Get organizations
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations") class Organizations()

    /**
     * Remove member from the specified organization
     *
     * @param organizationId Organization ID
     * @param userId User ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/members/{userId}") class OrganizationMember(val organizationId: java.util.UUID, val userId: java.util.UUID)

    /**
     * Get details about current user
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users/me") class UserAccountDetails()

    /**
     * Get organizations which the current user is a member of
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users/me/organizations") class UserOrganizations()

    /**
     * Get users associated with the current user by organization membership
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users") class Users()

    /**
     * Session termination
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users/session") class UserOut()

    /**
     * Get or remove specified workspace
     *
     * @param workspaceId Workspace ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/workspaces/{workspaceId}") class Workspace(val workspaceId: java.util.UUID)

    /**
     * Get workspaces which the current user has access to
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/workspaces") class Workspaces()

}
