package processm.core.log.hierarchical

import org.junit.jupiter.api.Tag
import processm.core.DBTestHelper
import processm.core.helpers.parseISO8601
import processm.core.log.Event
import processm.core.log.XESComponent
import processm.core.log.attribute.Attribute.Companion.CONCEPT_INSTANCE
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.log.attribute.Attribute.Companion.COST_CURRENCY
import processm.core.log.attribute.Attribute.Companion.COST_TOTAL
import processm.core.log.attribute.Attribute.Companion.IDENTITY_ID
import processm.core.log.attribute.Attribute.Companion.LIFECYCLE_MODEL
import processm.core.log.attribute.Attribute.Companion.LIFECYCLE_STATE
import processm.core.log.attribute.Attribute.Companion.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.Companion.ORG_GROUP
import processm.core.log.attribute.Attribute.Companion.ORG_RESOURCE
import processm.core.log.attribute.Attribute.Companion.ORG_ROLE
import processm.core.log.attribute.Attribute.Companion.TIME_TIMESTAMP
import processm.core.log.attribute.value
import processm.core.log.getStandardToCustomNameMap
import processm.core.logging.logger
import processm.core.querylanguage.Query
import java.util.*
import kotlin.test.assertTrue

@Tag("PQL")
open class DBHierarchicalXESInputStreamWithQueryTestsBase {
    companion object {
        val logger = logger()

        val journal: UUID = DBTestHelper.JournalReviewExtra
        val bpi: UUID = DBTestHelper.BPIChallenge2013OpenProblems
        val hospital: UUID = DBTestHelper.HospitalLog
        val eventNames = setOf(
            "invite reviewers",
            "time-out 1", "time-out 2", "time-out 3",
            "get review 1", "get review 2", "get review 3",
            "collect reviews",
            "decide",
            "invite additional reviewer",
            "get review X",
            "time-out X",
            "reject",
            "accept"
        )
        val lifecycleTransitions = setOf("start", "complete")
        val orgResources =
            setOf("__INVALID__", "Mike", "Anne", "Wil", "Pete", "John", "Mary", "Carol", "Sara", "Sam", "Pam")
        val results = setOf("accept", "reject")
        val begin = "2005-12-31T00:00:00.000Z".parseISO8601()
        val end = "2008-05-05T00:00:00.000Z".parseISO8601()
        val validCurrencies = setOf("EUR", "USD")
        val bpiEventNames = setOf("Accepted", "Completed", "Queued")
    }

    protected fun q(query: String): DBHierarchicalXESInputStream =
        DBHierarchicalXESInputStream(DBTestHelper.dbName, Query(query))

    protected fun standardAndAllAttributesMatch(log: Log, component: XESComponent) {
        val nameMap = getStandardToCustomNameMap(log)

        // Ignore comparison if there is no value in element.attributes.
        // This is because XESInputStream implementations are required to only map custom attributes to standard attributes
        // but not otherwise.
        fun cmp(standard: Any?, standardName: String) =
            assertTrue(standard == component.attributes[nameMap[standardName]]?.value || component.attributes[nameMap[standardName]]?.value == null)

        cmp(component.conceptName, CONCEPT_NAME)
        cmp(component.identityId, IDENTITY_ID)

        when (component) {
            is Log -> {
                cmp(component.lifecycleModel, LIFECYCLE_MODEL)
            }
            is Trace -> {
                cmp(component.costCurrency, COST_CURRENCY)
                cmp(component.costTotal, COST_TOTAL)
            }
            is Event -> {
                cmp(component.conceptInstance, CONCEPT_INSTANCE)
                cmp(component.costCurrency, COST_CURRENCY)
                cmp(component.costTotal, COST_TOTAL)
                cmp(component.lifecycleTransition, LIFECYCLE_TRANSITION)
                cmp(component.lifecycleState, LIFECYCLE_STATE)
                cmp(component.orgGroup, ORG_GROUP)
                cmp(component.orgResource, ORG_RESOURCE)
                cmp(component.orgRole, ORG_ROLE)
                cmp(component.timeTimestamp, TIME_TIMESTAMP)
            }
        }

    }

    private val nameMapCache: IdentityHashMap<Log, Map<String, String>> = IdentityHashMap()
    protected fun getStandardToCustomNameMap(log: Log): Map<String, String> = nameMapCache.computeIfAbsent(log) {
        it.extensions.values.getStandardToCustomNameMap()
    }
}
