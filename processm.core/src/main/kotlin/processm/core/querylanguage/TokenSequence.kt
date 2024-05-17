package processm.core.querylanguage

import org.apache.commons.text.StringEscapeUtils


/**
 * The type of [TokenSequence]
 */
enum class TokenSequenceType {

    /**
     * Unspecified type
     */
    Unknown,

    /**
     * End-of-file
     */
    EOF,

    /**
     * A normal sequence of tokens
     */
    Normal
}

/**
 * An auxiliary used by [PQLParserException] to distinguish between EOF and normal tokens
 */
data class TokenSequence private constructor(val type: TokenSequenceType, val value: String?) {

    companion object {
        val Unknown = TokenSequence(TokenSequenceType.Unknown, null)
        val EOF = TokenSequence(TokenSequenceType.EOF, null)

        private fun String.escapeWS() = StringEscapeUtils.escapeJava(this)
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