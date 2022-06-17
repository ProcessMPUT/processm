package processm.core.log.hierarchical

import org.junit.jupiter.api.Tag
import processm.core.DBTestHelper
import processm.core.helpers.parseISO8601
import processm.core.log.Event
import processm.core.log.XESComponent
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

        cmp(component.conceptName, "concept:name")
        cmp(component.identityId, "identity:id")

        when (component) {
            is Log -> {
                cmp(component.lifecycleModel, "lifecycle:model")
            }
            is Trace -> {
                cmp(component.costCurrency, "cost:currency")
                cmp(component.costTotal, "cost:total")
            }
            is Event -> {
                cmp(component.conceptInstance, "concept:instance")
                cmp(component.costCurrency, "cost:currency")
                cmp(component.costTotal, "cost:total")
                cmp(component.lifecycleTransition, "lifecycle:transition")
                cmp(component.lifecycleState, "lifecycle:state")
                cmp(component.orgGroup, "org:group")
                cmp(component.orgResource, "org:resource")
                cmp(component.orgRole, "org:role")
                cmp(component.timeTimestamp, "time:timestamp")
            }
        }

    }

    private val nameMapCache: IdentityHashMap<Log, Map<String, String>> = IdentityHashMap()
    protected fun getStandardToCustomNameMap(log: Log): Map<String, String> = nameMapCache.computeIfAbsent(log) {
        it.extensions.values.getStandardToCustomNameMap()
    }
}
