package processm.services.api

import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location

object Paths {
    /**
     * Get specified group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}") class getGroup(val groupId: java.util.UUID)

    /**
     * Get members of specified group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/members") class getGroupMembers(val groupId: java.util.UUID)

    /**
     * Get groups belonging to user organization
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups") class getGroups

    /**
     * Get subgroups of specified group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/subgroups") class getSubgroups(val groupId: java.util.UUID)

    /**
     * Remove user group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}") class removeGroup(val groupId: java.util.UUID)

    /**
     * Remove member from group
     *
     * @param groupId Group ID
     * @param userId User ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/members/{userId}") class removeGroupMember(val groupId: java.util.UUID, val userId: java.util.UUID)

    /**
     * Remove speciified subgroup
     *
     * @param groupId Group ID
     * @param subgroupId Subgroup ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/subgroups/{subgroupId}") class removeSubgroup(val groupId: java.util.UUID, val subgroupId: java.util.UUID)

    /**
     * Get specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}") class getOrganization(val organizationId: java.util.UUID)

    /**
     * Get groups associated with the organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/groups") class getOrganizationGroups(val organizationId: java.util.UUID)

    /**
     * Get members of specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/members") class getOrganizationMembers(val organizationId: java.util.UUID)

    /**
     * Get organizations
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations") class getOrganizations

    /**
     * Remove specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}") class removeOrganization(val organizationId: java.util.UUID)

    /**
     * Remove member from organization
     *
     * @param organizationId Organization ID
     * @param userId User ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/members/{userId}") class removeOrganizationMember(val organizationId: java.util.UUID, val userId: java.util.UUID)

    /**
     * Get details about current user
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users/me") class getUserAccountDetails

    /**
     * Get organizations which the current user is member of
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users/me/organizations") class getUserOrganizations

    /**
     * Get users associated with the current user by organization membership
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users") class getUsers

    /**
     * Session termination
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users/session") class signUserOut

    /**
     * Remove the specified workspace in the context of the specified organization
     *
     * @param organizationId Organization ID
     * @param workspaceId Workspace ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/workspaces/{workspaceId}") class deleteWorkspace(val organizationId: java.util.UUID, val workspaceId: java.util.UUID)

    /**
     * Get the specified workspace in the context of the specified organization
     *
     * @param organizationId Organization ID
     * @param workspaceId Workspace ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/workspaces/{workspaceId}") class getWorkspace(val organizationId: java.util.UUID, val workspaceId: java.util.UUID)

    /**
     * Get the specified component in the specified workspace in the context of the specified organization
     *
     * @param organizationId Organization ID
     * @param workspaceId Workspace ID
     * @param componentId Component ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/workspaces/{workspaceId}/components/{componentId}") class getWorkspaceComponent(val organizationId: java.util.UUID, val workspaceId: java.util.UUID, val componentId: java.util.UUID)

    /**
     * Get data of the specified component in the specified workspace in the context of the specified organization
     *
     * @param organizationId Organization ID
     * @param workspaceId Workspace ID
     * @param componentId Component ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/workspaces/{workspaceId}/components/{componentId}/data") class getWorkspaceComponentData(val organizationId: java.util.UUID, val workspaceId: java.util.UUID, val componentId: java.util.UUID)

    /**
     * Get all components available to the calling user in the specified workspace in the context of the specified organization
     *
     * @param organizationId Organization ID
     * @param workspaceId Workspace ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/workspaces/{workspaceId}/components") class getWorkspaceComponents(val organizationId: java.util.UUID, val workspaceId: java.util.UUID)

    /**
     * Get all workspaces available to the calling user in the context of the specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/workspaces") class getWorkspaces(val organizationId: java.util.UUID)

}
