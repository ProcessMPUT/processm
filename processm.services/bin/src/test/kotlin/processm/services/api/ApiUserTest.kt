package processm.services.api

import processm.services.api.models.OrganizationRole
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApiUserTest {
    @Test
    fun `object creation throws if insufficient claims are provided`() {
        assertFailsWith<ApiException> {
            ApiUser(mapOf())
        }
    }

    @Suppress("KotlinConstantConditions")
    @Test
    fun `role order check for ensureUserBelongsToOrganization`() {
        // ensureUserBelongsToOrganization() depends on OrganizationRole.*.ordinal which in turn depends on code generated from api-spec.yaml.
        // Since this is critical part from security perspective, we verify whether the order of roles is as expected by ensureUserBelongsToOrganization().
        assertTrue(OrganizationRole.owner.ordinal < OrganizationRole.writer.ordinal)
        assertTrue(OrganizationRole.writer.ordinal < OrganizationRole.reader.ordinal)
        assertTrue(OrganizationRole.reader.ordinal < OrganizationRole.none.ordinal)
    }
}
