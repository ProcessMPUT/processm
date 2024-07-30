package processm.etl.jdbc.nosql

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Copied verbatim from JsonElementSerializers
 */
@OptIn(ExperimentalSerializationApi::class)
private fun defer(deferred: () -> SerialDescriptor): SerialDescriptor = object : SerialDescriptor {

    private val original: SerialDescriptor by lazy(deferred)

    override val serialName: String
        get() = original.serialName
    override val kind: SerialKind
        get() = original.kind
    override val elementsCount: Int
        get() = original.elementsCount

    override fun getElementName(index: Int): String = original.getElementName(index)
    override fun getElementIndex(name: String): Int = original.getElementIndex(name)
    override fun getElementAnnotations(index: Int): List<Annotation> = original.getElementAnnotations(index)
    override fun getElementDescriptor(index: Int): SerialDescriptor = original.getElementDescriptor(index)
    override fun isElementOptional(index: Int): Boolean = original.isElementOptional(index)
}

private object QueryItemSerializer : KSerializer<QueryItem> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(QueryItemSerializer::class.qualifiedName!!) {
        element("map", defer { MapSerializer(String.serializer(), QueryItemSerializer).descriptor }, isOptional = true)
        element("list", defer { ListSerializer(QueryItemSerializer).descriptor }, isOptional = true)
        element("literal", JsonPrimitive.serializer().descriptor, isOptional = true)
        element("parameter", JsonElement.serializer().descriptor, isOptional = true)
    }

    override fun deserialize(decoder: Decoder): QueryItem =
        throw UnsupportedOperationException("Intentionally not supported")

    override fun serialize(encoder: Encoder, value: QueryItem) {
        with(value) {
            when {
                map !== null -> MapSerializer(String.serializer(), QueryItemSerializer).serialize(encoder, map)
                list !== null -> ListSerializer(QueryItemSerializer).serialize(encoder, list)
                literal !== null -> JsonPrimitive.serializer().serialize(encoder, literal)
                parameter !== null -> Parameter.serializer().serialize(encoder, parameter)
            }
        }
    }

}

@Serializable(with = QueryItemSerializer::class)
internal data class QueryItem(
    val map: Map<String, QueryItem>? = null,
    val list: List<QueryItem>? = null,
    val literal: JsonPrimitive? = null,
    val parameter: Parameter? = null
) {

    init {
        var ctr = 0
        if (map !== null) ctr++
        if (list !== null) ctr++
        if (literal !== null) ctr++
        if (parameter !== null) ctr++
        require(ctr == 1) { "Expected exactly 1 field to be not null, found $ctr" }
    }

}

private object ParameterSerializer : KSerializer<Parameter> {
    override val descriptor: SerialDescriptor
        get() = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): Parameter =
        throw UnsupportedOperationException("Intentionally not supported")

    override fun serialize(encoder: Encoder, value: Parameter) =
        JsonElement.serializer().serialize(encoder, checkNotNull(value.currentValue))

}

@Serializable(with = ParameterSerializer::class)
internal class Parameter {
    var currentValue: JsonElement? = null
}

/**
 * Parsed CouchDB query with binding variables. Any JSON literal equal to ? is assumed to be a binding variable.
 * To actually construct a literal of n question marks one must use a literal of n+1 question marks, i.e.:
 * * "?" represents a binding variable
 * * "??" represents the literal "?"
 * * "???" represents the literal "??"
 * etc.
 */
internal abstract class JSONQuery {
    // maintain order, as the initializer for parsed (i.e., substitute) relies on parameters and bookmark
    protected val parameters = ArrayList<Parameter>()
    protected abstract val parsed: QueryItem

    val parameterCount: Int
        get() = parameters.size

    protected open fun substitutePrimitive(primitive: JsonPrimitive): QueryItem {
        val content = primitive.contentOrNull
        if (content.isNullOrBlank() || !content.all { it == '?' }) return QueryItem(literal = primitive)
        if (content.length == 1) {
            assert(content == "?")
            return QueryItem(parameter = Parameter().also { parameters.add(it) })
        }
        return QueryItem(literal = JsonPrimitive(content.substring(1)))
    }

    protected open fun substituteArray(array: JsonArray): QueryItem = QueryItem(list = array.map(this::substitute))

    protected open fun substituteObject(obj: JsonObject): QueryItem =
        QueryItem(map = obj.mapValues { substitute(it.value) })


    protected open fun substitute(element: JsonElement): QueryItem = when (element) {
        is JsonPrimitive -> substitutePrimitive(element)
        is JsonArray -> substituteArray(element)
        is JsonObject -> substituteObject(element)
        else -> throw IllegalArgumentException("Unexpected type ${element::class}")
    }

    /**
     * Returns a JSON representation of the query with the binding variables replaced by [values].
     * @throws IllegalArgumentException If `values.size != parameterCount`
     */
    open fun bind(values: List<JsonElement>): String {
        require(parameters.size == values.size)
        for (i in parameters.indices) parameters[i].currentValue = values[i]
        return Json.encodeToString(parsed)
    }
}