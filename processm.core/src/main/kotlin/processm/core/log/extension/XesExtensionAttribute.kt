package processm.core.log.extension

import java.util.*

class XesExtensionAttribute(key: String, type: String) {
    internal val mapping: MutableMap<String, String> = TreeMap(String.CASE_INSENSITIVE_ORDER)

    /**
     * The key of the attribute
     * Format: string
     * Required: true
     */
    val key: String = key.intern()

    /**
     * The format of the attribute
     * Format: string, one of: `string`, `float`, `int`, `id`, `list`, `date`
     * Required: true
     */
    val type: String = type.intern()

    /**
     * The alias extension element from the XES metadata structure.
     *
     * Key: The language code (using the ISO 639-1 and ISO 639-2 standards) for this alias.
     * Value: The semantics of this attribute described using the language with the given code
     */
    val aliases: Map<String, String>
        get() = Collections.unmodifiableMap(mapping)
}