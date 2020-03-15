package processm.core.querylanguage

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.util.*
import kotlin.collections.LinkedHashSet
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Represents log query as parsed from the string given as constructor argument.
 * @property query The string representation of the query.
 * @throws RecognitionException
 * @throws IllegalArgumentException
 */
@Suppress("MapGetWithNotNullAssertionOperator")
class Query(val query: String) {
    companion object {
        private const val LOG_MASK: Byte = 0b00000100
        private const val TRACE_MASK: Byte = 0b00000010
        private const val EVENT_MASK: Byte = 0b00000001
        private const val EMPTY_MASK: Byte = 0b00000000
        private const val ALL_SCOPES_MASK: Byte = 0b00000111
    }

    private val errorListener: ErrorListener = ErrorListener()
    private val stream: CodePointCharStream = CharStreams.fromString(query)
    private val lexer: QLLexer = QLLexer(stream)
    private val tokens: CommonTokenStream = CommonTokenStream(lexer)
    private val parser: QueryLanguage = QueryLanguage(tokens)

    init {
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
    }


    // region data model
    // region SQL select clause
    private var _selectAll: Byte = 0

    /**
     * Whether to select all (standard and non-standard) attributes.
     */
    var selectAll: Boolean
        get() = _selectAll == ALL_SCOPES_MASK
        set(value) {
            _selectAll = if (value) ALL_SCOPES_MASK else EMPTY_MASK
        }

    /**
     * Whether to select all (standard and non-standard) attributes on log scope.
     */
    var selectAllLog: Boolean
        get() = _selectAll and LOG_MASK != 0.toByte()
        private set(value) {
            _selectAll = _selectAll or LOG_MASK
            if (value) {
                _selectStandardAttributes[Scope.Log]!!.clear()
                _selectOtherAttributes[Scope.Log]!!.clear()
            }
        }

    /**
     * Whether to select all (standard and non-standard) attributes on trace scope.
     */
    var selectAllTrace: Boolean
        get() = _selectAll and TRACE_MASK != 0.toByte()
        private set(value) {
            _selectAll = _selectAll or TRACE_MASK
            if (value) {
                _selectStandardAttributes[Scope.Trace]!!.clear()
                _selectOtherAttributes[Scope.Trace]!!.clear()
            }
        }

    /**
     * Whether to select all (standard and non-standard) attributes on event scope.
     */
    var selectAllEvent: Boolean
        get() = _selectAll and EVENT_MASK != 0.toByte()
        private set(value) {
            _selectAll = _selectAll or EVENT_MASK
            if (value) {
                _selectStandardAttributes[Scope.Event]!!.clear()
                _selectOtherAttributes[Scope.Event]!!.clear()
            }
        }

    private val _selectStandardAttributes: Map<Scope, LinkedHashSet<PQLAttribute>> = mapOf(
        Scope.Log to LinkedHashSet(),
        Scope.Trace to LinkedHashSet(),
        Scope.Event to LinkedHashSet()
    )

    private val _selectOtherAttributes: Map<Scope, LinkedHashSet<PQLAttribute>> = mapOf(
        Scope.Log to LinkedHashSet(),
        Scope.Trace to LinkedHashSet(),
        Scope.Event to LinkedHashSet()
    )

    private val _selectExpressions: Map<Scope, LinkedHashSet<Expression>> = mapOf(
        Scope.Log to LinkedHashSet(),
        Scope.Trace to LinkedHashSet(),
        Scope.Event to LinkedHashSet()
    )

    /**
     * The standard attributes to select on the log scope.
     */
    val selectLogStandardAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_selectStandardAttributes[Scope.Log])

    /**
     * The standard attributes to select on the trace scope.
     */
    val selectTraceStandardAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_selectStandardAttributes[Scope.Trace])

    /**
     * The standard attributes to select on the event scope.
     */
    val selectEventStandardAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_selectStandardAttributes[Scope.Event])

    /**
     * The non-standard attributes to select on the log scope.
     */
    val selectLogOtherAttributes: Set<PQLAttribute> = Collections.unmodifiableSet(_selectOtherAttributes[Scope.Log])

    /**
     * The non-standard attributes to select on the trace scope.
     */
    val selectTraceOtherAttributes: Set<PQLAttribute> = Collections.unmodifiableSet(_selectOtherAttributes[Scope.Trace])

    /**
     * The non-standard attributes to select on the event scope.
     */
    val selectEventOtherAttributes: Set<PQLAttribute> = Collections.unmodifiableSet(_selectOtherAttributes[Scope.Event])

    /**
     * The expressions to select on the log scope.
     */
    val selectLogExpressions: Set<Expression> = Collections.unmodifiableSet(_selectExpressions[Scope.Log])

    /**
     * The expressions to select on the trace scope.
     */
    val selectTraceExpressions: Set<Expression> = Collections.unmodifiableSet(_selectExpressions[Scope.Trace])

    /**
     * The expressions to select on the event scope.
     */
    val selectEventExpressions: Set<Expression> = Collections.unmodifiableSet(_selectExpressions[Scope.Event])
    // endregion
    // region SQL join clause

    // endregion
    // endregion
    init {
        assert(Query::class.memberProperties.all {
            it.isAccessible = true; it.get(this) !== null
        }) { "This init{} block must be located after all properties!" }

        val tree = parser.query()
        val walker = ParseTreeWalker()
        walker.walk(Listener(), tree)
        if (errorListener.error !== null)
            throw errorListener.error!!
    }

    override fun toString(): String = query


    private inner class Listener : QueryLanguageBaseListener() {
        // region PQL select clause

        override fun exitSelect_all(ctx: QueryLanguage.Select_allContext?) {
            selectAll = true
        }

        override fun enterScoped_select_all(ctx: QueryLanguage.Scoped_select_allContext?) {
            val token = ctx!!.SCOPE().text
            when (Scope.parse(token)) {
                Scope.Log -> selectAllLog = true
                Scope.Trace -> selectAllTrace = true
                Scope.Event -> selectAllEvent = true
            }
        }

        override fun exitArith_expr_root(ctx: QueryLanguage.Arith_expr_rootContext?) {
            val interval = ctx!!.sourceInterval
            if (interval.length() == 1 && tokens.get(interval.a).type == QueryLanguage.ID) {
                val attribute = PQLAttribute(ctx.text)
                if (attribute.hoistingPrefix.isNotEmpty())
                    throw IllegalArgumentException("Scope hoisting is not supported in the select clause. Line ${ctx.start.line} position ${ctx.start.charPositionInLine}.")

                val scopedSelectAll = when (attribute.scope) {
                    Scope.Log -> selectAllLog
                    Scope.Trace -> selectAllTrace
                    Scope.Event -> selectAllEvent
                }

                if (scopedSelectAll)
                    return

                when (attribute.isStandard) {
                    true -> _selectStandardAttributes[attribute.scope]
                    false -> _selectOtherAttributes[attribute.scope]
                }!!.add(attribute)
            } else {
                var currentScope: Scope? = null
                val exprArray = tokens.get(interval.a, interval.b).map {
                    val attributeOrLiteral = parseAttributeOrLiteral(it, currentScope ?: Scope.Event)
                    if (attributeOrLiteral !== null) {
                        if (currentScope === null)
                            currentScope = attributeOrLiteral.scope
                        else if (currentScope!! < attributeOrLiteral.scope)
                            currentScope = attributeOrLiteral.scope

                        if (attributeOrLiteral is PQLAttribute) {
                            if (attributeOrLiteral.hoistingPrefix.isNotEmpty())
                                throw IllegalArgumentException("Scope hoisting is not supported in the select clause. Line ${it.line} position ${it.charPositionInLine}.")
                        }
                        attributeOrLiteral
                    } else Operator(it.text)
                }.toTypedArray()

                _selectExpressions[currentScope]!!.add(if (exprArray.size == 1) exprArray.first() else Expression(*exprArray))
            }
        }

        private fun parseAttributeOrLiteral(token: Token, defaultScope: Scope): Expression? = when (token.type) {
            QueryLanguage.ID -> PQLAttribute(token.text)
            QueryLanguage.BOOLEAN -> BooleanLiteral(token.text, defaultScope)
            QueryLanguage.NUMBER -> NumberLiteral(token.text, defaultScope)
            QueryLanguage.DATETIME -> DateTimeLiteral(token.text, defaultScope)
            QueryLanguage.STRING -> StringLiteral(token.text, defaultScope)
            QueryLanguage.NULL -> NullLiteral(token.text, defaultScope)
            else -> null
        }

        // endregion
    }

    private inner class ErrorListener : BaseErrorListener() {
        var error: RecognitionException? = null
            private set

        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String?,
            e: RecognitionException?
        ) {
            assert(e !== null)
            val eWithMessage = RecognitionException(
                "Line $line position $charPositionInLine: $msg",
                e!!.recognizer,
                e.inputStream,
                e.ctx as ParserRuleContext?
            )
            if (error === null)
                error = eWithMessage
            else
                error!!.addSuppressed(eWithMessage)
        }
    }
}