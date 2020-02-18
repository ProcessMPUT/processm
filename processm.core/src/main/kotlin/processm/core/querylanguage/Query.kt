package processm.core.querylanguage

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.text.NumberFormat
import java.util.*

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
