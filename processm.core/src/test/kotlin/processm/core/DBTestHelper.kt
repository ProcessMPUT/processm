package processm.core

import java.util.*

object DBTestHelper {
    /**
     * Test's db name - random generated but used in each test case.
     * Should reduce time in test step.
     */
    val dbName = UUID.randomUUID().toString()
}