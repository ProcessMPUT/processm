package processm.core.log

import processm.core.log.hierarchical.DatabaseHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.log.hierarchical.Log as HLog

class DatabaseXESInputStream(logId: Int) : XESInputStream {
    private val baseStream: Sequence<HLog> = DatabaseHierarchicalXESInputStream(logId) as Sequence<HLog>
    override fun iterator(): Iterator<XESElement> = baseStream.toFlatSequence().iterator()
}