package processm.etl.jdbc.nosql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive


/**
 * Parsed CouchDB query with binding variables. Any JSON literal equal to ? is assumed to be a binding variable.
 * To actually construct a literal of n question marks one must use a literal of n+1 question marks, i.e.:
 * * "?" represents a binding variable
 * * "??" represents the literal "?"
 * * "???" represents the literal "??"
 * etc.
 */
internal class CouchDBQuery(query: String) : JSONQuery() {
    private val bookmark = Parameter()
    override val parsed: QueryItem = substituteRoot(Json.parseToJsonElement(query))

    private fun substituteRoot(element: JsonElement): QueryItem {
        val base = substitute(element)
        return if (base.map !== null) {
            QueryItem(map = base.map.toMutableMap().apply {
                put("bookmark", QueryItem(parameter = bookmark))
            })
        } else
            base
    }

    override fun bind(values: List<JsonElement>): String = bind(values, null)

    /**
     * Returns a JSON representation of the query with the binding variables replaced by [values].
     * @param bookmark The value for the bookmark query parameter, used in pagination. Defaults to `null`.
     * @throws IllegalArgumentException If `values.size != parameterCount`
     */
    fun bind(values: List<JsonElement>, bookmark: String?): String {
        this.bookmark.currentValue = bookmark?.let { JsonPrimitive(it) } ?: JsonNull
        return super.bind(values)
    }
}