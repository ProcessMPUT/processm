package processm.etl.jdbc.nosql

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject


data class JsonPath(val items: List<String>) {
    companion object {
        private fun parse(query: String): List<String> {
            val part = StringBuilder()
            val result = ArrayList<String>()
            var esc = false
            var quot = false
            for (c in query) {
                if (esc) {
                    esc = false
                    part.append(c)
                    continue
                }
                if (c == '\\') {
                    esc = true
                    continue
                }
                if (c == '"') {
                    quot = !quot
                    continue
                }
                if (quot) {
                    part.append(c)
                    continue
                }
                if (c == '.') {
                    check(part.isNotEmpty())
                    result.add(part.toString())
                    part.clear()
                    continue
                }
                part.append(c)
            }
            check(!esc)
            check(!quot)
            check(part.isNotEmpty())
            result.add(part.toString())
            return result
        }

        private fun escape(text: String): String =
            text.replace("\\", "\\\\").replace(".", "\\.").replace("\"", "\\\"")
    }

    constructor(query: String) : this(parse(query))

    override fun toString(): String = items.joinToString(separator = ".") { escape(it) }
}

operator fun JsonElement.get(path: JsonPath): JsonElement? {
    var element = this
    for (item in path.items) {
        element = if (element is JsonArray) {
            val v = item.toIntOrNull() ?: return null
            if (v in element.indices)
                element[v]
            else
                return null
        } else if (element is JsonObject) element[item] ?: return null
        else return null
    }
    return element
}