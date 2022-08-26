package processm.core.models.processtree

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeSerializationTest {

    private inline fun <reified T> test(node: T) {
        val deserialized = Json.decodeFromString(serializer<T>(), Json.encodeToString(node))
        assertEquals(node, deserialized)
    }
    
    @Test
    fun `named activity`() {
        test(ProcessTreeActivity("a"))
    }

}