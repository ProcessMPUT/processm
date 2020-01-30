package processm.core.persistence

import kotlin.test.Test

class VolatilePersistenceProviderTests : PersistenceProviderBaseTests() {

    @Test
    fun putGetDeleteTest() {
        val provider = VolatilePersistenceProvider()
        super.putGetDeleteTest(provider)
    }

}