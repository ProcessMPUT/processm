package processm.core

import java.util.*

object Brand {
    const val name: String = "ProcessM"
    const val mainDBInternalName: String = "processm"

    /**
     * Test's db name - random generated but used in each test case.
     * Should reduce time in test step.
     */
    val dbName = UUID.randomUUID().toString()
}