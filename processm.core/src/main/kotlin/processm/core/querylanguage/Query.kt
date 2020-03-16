package processm.core.querylanguage

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.util.*
import kotlin.collections.LinkedHashSet
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.max
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

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

    // region parser
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
    // endregion

    // region data model
    /**
     * The first warning emitted during parsing of this query. Subsequent warnings are stored as suppressed exceptions.
     * The null value refers to no warnings.
     *
     * An exception is considered a warning, if its cause does not change semantics of the query, and so the query
     * yields the same results with and without this cause. E.g., using an outer-scope attribute in the group by clause
     * is considered a warning, because no matter whether this attribute is used or not the result of the query would be
     * the same.
     *
     * @see Throwable.getSuppressed
     */
    val warning: Exception?
        get() = errorListener.warning

    // region select clause
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
        Collections.unmodifiableSet(_selectStandardAttributes[Scope.Log]!!)

    /**
     * The standard attributes to select on the trace scope.
     */
    val selectTraceStandardAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_selectStandardAttributes[Scope.Trace]!!)

    /**
     * The standard attributes to select on the event scope.
     */
    val selectEventStandardAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_selectStandardAttributes[Scope.Event]!!)

    /**
     * The non-standard attributes to select on the log scope.
     */
    val selectLogOtherAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_selectOtherAttributes[Scope.Log]!!)

    /**
     * The non-standard attributes to select on the trace scope.
     */
    val selectTraceOtherAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_selectOtherAttributes[Scope.Trace]!!)

    /**
     * The non-standard attributes to select on the event scope.
     */
    val selectEventOtherAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_selectOtherAttributes[Scope.Event]!!)

    /**
     * The expressions to select on the log scope.
     */
    val selectLogExpressions: Set<Expression> = Collections.unmodifiableSet(_selectExpressions[Scope.Log]!!)

    /**
     * The expressions to select on the trace scope.
     */
    val selectTraceExpressions: Set<Expression> = Collections.unmodifiableSet(_selectExpressions[Scope.Trace]!!)

    /**
     * The expressions to select on the event scope.
     */
    val selectEventExpressions: Set<Expression> = Collections.unmodifiableSet(_selectExpressions[Scope.Event]!!)

    // endregion
    // region where clause
    /**
     * The expression in the where clause.
     */
    var whereExpression: Expression = Expression.empty()
        private set

    // endregion

    // region group by clause
    // However PQL does not allow for many group by clauses, as of version 0.1, I expect that the support will be
    // added in a future version. So the below collections hold separate sets of attributes for each scope.
    private val _groupByStandardAttributes: Map<Scope, LinkedHashSet<PQLAttribute>> = mapOf(
        Scope.Log to LinkedHashSet(),
        Scope.Trace to LinkedHashSet(),
        Scope.Event to LinkedHashSet()
    )
    private val _groupByOtherAttributes: Map<Scope, LinkedHashSet<PQLAttribute>> = mapOf(
        Scope.Log to LinkedHashSet(),
        Scope.Trace to LinkedHashSet(),
        Scope.Event to LinkedHashSet()
    )

    /**
     * The standard attributes used for grouping on the log scope.
     */
    val groupLogByStandardAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_groupByStandardAttributes[Scope.Log]!!)

    /**
     * The standard attributes used for grouping on the trace scope.
     */
    val groupTraceByStandardAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_groupByStandardAttributes[Scope.Trace]!!)

    /**
     * The standard attributes used for grouping on the event scope.
     */
    val groupEventByStandardAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_groupByStandardAttributes[Scope.Event]!!)

    /**
     * The non-standard attributes used for grouping on the log scope.
     */
    val groupLogByOtherAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_groupByOtherAttributes[Scope.Log]!!)

    /**
     * The non-standard attributes used for grouping on the trace scope.
     */
    val groupTraceByOtherAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_groupByOtherAttributes[Scope.Trace]!!)

    /**
     * The non-standard attributes used for grouping on the event scope.
     */
    val groupEventByOtherAttributes: Set<PQLAttribute> =
        Collections.unmodifiableSet(_groupByOtherAttributes[Scope.Event]!!)

    // end region
    // endregion
    init {
        assert(Query::class.memberProperties.all {
            it.isAccessible = true; it.javaField === null || it.get(this) !== null
        }) { "This init{} block must be located after all properties!" }

        val tree = parser.query()
        val walker = ParseTreeWalker()
        walker.walk(Listener(), tree)
        validateGroupByAttributes()
        if (errorListener.error !== null)
            throw errorListener.error!!
    }

    private fun validateGroupByAttributes() {
        fun validate(toValidate: Map<Scope, Set<PQLAttribute>>, groupByMap: Map<Scope, Set<PQLAttribute>>) =
            toValidate
                .flatMap { it.value }
                .filter {
                    var groupByEnabled = false
                    var scope: Scope? = it.scope
                    do {
                        if (_groupByStandardAttributes[scope]!!.size != 0 || _groupByOtherAttributes[scope]!!.size != 0) {
                            groupByEnabled = true
                            if (it in groupByMap[scope]!!)
                                return@filter false // valid use
                        }
                        scope = scope!!.upper
                    } while (scope !== null)
                    return@filter groupByEnabled
                }.forEach {
                    errorListener.delayedThrow(
                        IllegalArgumentException(
                            "Line ${it.line} position ${it.charPositionInLine}: The attribute $it from the select clause is not in the group by clause. Such attributes can be used only as an argument of an aggregation function."
                        )
                    )
                }

        validate(_selectStandardAttributes, _groupByStandardAttributes)
        validate(_selectOtherAttributes, _groupByOtherAttributes)
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
            val expression = parseExpression(ctx!!.sourceInterval)
            val hoisted = (sequenceOf(expression) + expression.children).firstOrNull {
                it is PQLAttribute && it.hoistingPrefix.isNotEmpty()
            }
            if (hoisted !== null)
                errorListener.delayedThrow(IllegalArgumentException("Line ${hoisted.line} position ${hoisted.charPositionInLine}: Scope hoisting is not supported in the select clause."))

            if (expression is PQLAttribute) {
                val scopedSelectAll = when (expression.effectiveScope) {
                    Scope.Log -> selectAllLog
                    Scope.Trace -> selectAllTrace
                    Scope.Event -> selectAllEvent
                }

                if (scopedSelectAll)
                    return

                when (expression.isStandard) {
                    true -> _selectStandardAttributes[expression.effectiveScope]
                    false -> _selectOtherAttributes[expression.effectiveScope]
                }!!.add(expression)
            } else {
                _selectExpressions[expression.effectiveScope]!!.add(expression)
            }
        }
        // endregion

        // region PQL where clause
        override fun exitWhere(ctx: QueryLanguage.WhereContext?) {
            val interval = ctx!!.sourceInterval
            whereExpression = parseExpression(Interval.of(interval.a + 1, interval.b))
        }
        // endregion

        // region PQL group by clause

        override fun exitGroup_trace_by(ctx: QueryLanguage.Group_trace_byContext?) {
            handleGroupByIdList(Scope.Trace, ctx!!.id_list().sourceInterval)
        }

        override fun exitGroup_scope_by(ctx: QueryLanguage.Group_scope_byContext?) {
            val scope = Scope.parse(ctx!!.SCOPE().text)
            handleGroupByIdList(scope, ctx.id_list().sourceInterval)
        }

        private fun handleGroupByIdList(scope: Scope, interval: Interval) {
            val standardAttributes = _groupByStandardAttributes[scope]!!
            val otherAttributes = _groupByOtherAttributes[scope]!!

            tokens.get(interval.a, interval.b)
                .filter { it.type == QueryLanguage.ID }
                .map { PQLAttribute(it.text, it.line, it.charPositionInLine) }
                .filter {
                    if (it.effectiveScope < scope) {
                        errorListener.emitWarning(
                            IllegalArgumentException(
                                "Line ${it.line} position ${it.charPositionInLine}: Use of the attribute $it with effective scope ${it.effectiveScope} in group by clause with scope $scope is meaningless. Attribute dropped."
                            )
                        )
                        false
                    } else true
                }.forEach {
                    if (it.isStandard)
                        standardAttributes.add(it)
                    else
                        otherAttributes.add(it)
                }
        }


        // endregion

        private fun parseExpression(interval: Interval): Expression {
            val exprArray = tokens.get(interval.a, max(interval.b, interval.a))
                .mapNotNull(::parseExpression)
                .toTypedArray()
            return if (exprArray.size == 1) exprArray[0] else Expression(*exprArray)
        }

        private fun parseExpression(token: Token): Expression? = try {
            when (token.type) {
                QueryLanguage.ID -> PQLAttribute(token.text, token.line, token.charPositionInLine)
                QueryLanguage.BOOLEAN -> BooleanLiteral(token.text, token.line, token.charPositionInLine)
                QueryLanguage.NUMBER -> NumberLiteral(token.text, token.line, token.charPositionInLine)
                QueryLanguage.DATETIME -> DateTimeLiteral(token.text, token.line, token.charPositionInLine)
                QueryLanguage.STRING -> StringLiteral(token.text, token.line, token.charPositionInLine)
                QueryLanguage.NULL -> NullLiteral(token.text, token.line, token.charPositionInLine)
                else -> Operator(token.text, token.line, token.charPositionInLine)
            }
        } catch (e: Exception) {
            errorListener.delayedThrow(e)
            null
        }
    }

    private inner class ErrorListener : BaseErrorListener() {
        var error: Exception? = null
            private set

        var warning: Exception? = null
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
            delayedThrow(eWithMessage)
        }

        fun delayedThrow(exception: Exception) {
            if (error === null)
                error = exception
            else
                error!!.addSuppressed(exception)
        }

        fun emitWarning(exception: Exception) {
            if (warning === null)
                warning = exception
            else
                warning!!.addSuppressed(exception)
        }
    }
}