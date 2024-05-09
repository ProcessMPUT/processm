package processm.core.querylanguage


enum class TokenSequenceType {
    Unknown,
    EOF,
    Normal
}

/**
 * An auxiliary used by [PQLParserError] to distinguish between EOF and normal tokens
 */
data class TokenSequence private constructor(val type: TokenSequenceType, val value: String?) {

    companion object {
        val Unknown = TokenSequence(TokenSequenceType.Unknown, null)
        val EOF = TokenSequence(TokenSequenceType.EOF, null)

        private fun String.escapeWS() = replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }

    constructor(value: String) : this(TokenSequenceType.Normal, value.escapeWS())

    init {
        assert((type == TokenSequenceType.Normal) == (value !== null))
    }

    override fun toString(): String = when (type) {
        TokenSequenceType.Unknown -> "<unknown>"
        TokenSequenceType.EOF -> "<EOF>"
        TokenSequenceType.Normal -> "'$value'"
    }
}