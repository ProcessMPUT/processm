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
    @Location("/groups/{groupId}")
    class getGroup(val groupId: kotlin.String)

    /**
     * Get members of specified group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/members")
    class getGroupMembers(val groupId: kotlin.String)

    /**
     * Get groups belonging to user organization
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups")
    class getGroups

    /**
     * Get subgroups of specified group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/subgroups")
    class getSubgroups(val groupId: kotlin.String)

    /**
     * Remove user group
     *
     * @param groupId Group ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}")
    class removeGroup(val groupId: kotlin.String)

    /**
     * Remove member from group
     *
     * @param groupId Group ID
     * @param userId User ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/members/{userId}")
    class removeGroupMember(val groupId: kotlin.String, val userId: kotlin.String)

    /**
     * Remove speciified subgroup
     *
     * @param groupId Group ID
     * @param subgroupId Subgroup ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/groups/{groupId}/subgroups/{subgroupId}")
    class removeSubgroup(val groupId: kotlin.String, val subgroupId: kotlin.String)

    /**
     * Get specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}")
    class getOrganization(val organizationId: kotlin.String)

    /**
     * Get members of specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/members")
    class getOrganizationMembers(val organizationId: kotlin.String)

    /**
     * Get organizations
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations")
    class getOrganizations

    /**
     * Remove specified organization
     *
     * @param organizationId Organization ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}")
    class removeOrganization(val organizationId: kotlin.String)

    /**
     * Remove member from organization
     *
     * @param organizationId Organization ID
     * @param userId User ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/organizations/{organizationId}/members/{userId}")
    class removeOrganizationMember(val organizationId: kotlin.String, val userId: kotlin.String)

    /**
     * Get details about current user
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users/me")
    class getUserAccountDetails

    /**
     * Get users associated with the current user by organization membership
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users")
    class getUsers

    /**
     * Session termination
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/users/session")
    class signUserOut

    /**
     * Remove specified workspace
     *
     * @param workspaceId Workspace ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/workspaces/{workspaceId}")
    class deleteWorkspace(val workspaceId: kotlin.String)

    /**
     * Get specified workspace
     *
     * @param workspaceId Workspace ID
     */
    @KtorExperimentalLocationsAPI
    @Location("/workspaces/{workspaceId}")
    class getWorkspace(val workspaceId: kotlin.String)

    /**
     * Get workspaces which user has access to
     *
     */
    @KtorExperimentalLocationsAPI
    @Location("/workspaces")
    class getWorkspaces

}
