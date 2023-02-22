package processm.core.helpers

class HashMapIntern {
    private val data = HashMap<String, String>()
    operator fun invoke(key: String): String = data.computeIfAbsent(key) { it }
}