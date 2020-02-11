package processm.core.querylanguage

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

/**
 * Represents log query as parsed from the string given as constructor argument.
 * @property query The string representation of the query.
 * @throws org.antlr.v4.runtime.RecognitionException
 */
class Query(val query: String) {
    /**
     * Parse tree.
     */
    //val tree: QueryLanguage.QueryContext

    init {
        val stream = CharStreams.fromString(query)
        val lexer = QLLexer(stream)
        var tokens = CommonTokenStream(lexer)
        val parser = QueryLanguage(tokens)
        val tree = parser.query()
    }
}

fun test() {
    val qName = "s"
    when {
        qName.equals("extension", true) -> TODO()
        qName.equals("global", true) -> TODO()
        qName.equals("classifier", true) -> TODO()
    }
}