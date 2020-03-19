package processm.core.querylanguage

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.util.*
import kotlin.collections.LinkedHashSet

/**
 * Represents log query as parsed from the string given as constructor argument.
 * @property query The string representation of the query.
 * @throws RecognitionException
 * @throws IllegalArgumentException
 */
@Suppress("MapGetWithNotNullAssertionOperator")
class Query(val query: String) {
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
    /**
     * Whether the select all clause is not specified explicitly, but it is implied by the query structure.
     */
    var isImplicitSelectAll: Boolean = false
        private set
    private var _selectAllLog: Boolean? = null
    private var _selectAllTrace: Boolean? = null
    private var _selectAllEvent: Boolean? = null

    /**
     * Whether to select all (standard and non-standard) attributes.
     */
    val selectAll: Boolean
        get() = selectAllLog && selectAllTrace && selectAllEvent

    /**
     * Whether to select all (standard and non-standard) attributes on log scope.
     */
    val selectAllLog: Boolean
        get() = _selectAllLog ?: isImplicitSelectAll

    /**
     * Whether to select all (standard and non-standard) attributes on trace scope.
     */
    val selectAllTrace: Boolean
        get() = _selectAllTrace ?: isImplicitSelectAll

    /**
     * Whether to select all (standard and non-standard) attributes on event scope.
     */
    val selectAllEvent: Boolean
        get() = _selectAllEvent ?: isImplicitSelectAll

    private val _selectStandardAttributes: Map<Scope, LinkedHashSet<Attribute>> = mapOf(
        Scope.Log to LinkedHashSet(),
        Scope.Trace to LinkedHashSet(),
        Scope.Event to LinkedHashSet()
    )

    private val _selectOtherAttributes: Map<Scope, LinkedHashSet<Attribute>> = mapOf(
        Scope.Log to LinkedHashSet(),
        Scope.Trace to LinkedHashSet(),
        Scope.Event to LinkedHashSet()
    )

    private val _selectExpressions: Map<Scope, ArrayList<Expression>> = mapOf(
        Scope.Log to ArrayList(),
        Scope.Trace to ArrayList(),
        Scope.Event to ArrayList()
    )

    /**
     * The standard attributes to select on the log scope.
     */
    val selectLogStandardAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_selectStandardAttributes[Scope.Log]!!)

    /**
     * The standard attributes to select on the trace scope.
     */
    val selectTraceStandardAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_selectStandardAttributes[Scope.Trace]!!)

    /**
     * The standard attributes to select on the event scope.
     */
    val selectEventStandardAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_selectStandardAttributes[Scope.Event]!!)

    /**
     * The non-standard attributes to select on the log scope.
     */
    val selectLogOtherAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_selectOtherAttributes[Scope.Log]!!)

    /**
     * The non-standard attributes to select on the trace scope.
     */
    val selectTraceOtherAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_selectOtherAttributes[Scope.Trace]!!)

    /**
     * The non-standard attributes to select on the event scope.
     */
    val selectEventOtherAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_selectOtherAttributes[Scope.Event]!!)

    /**
     * The expressions to select on the log scope.
     */
    val selectLogExpressions: List<Expression> = Collections.unmodifiableList(_selectExpressions[Scope.Log]!!)

    /**
     * The expressions to select on the trace scope.
     */
    val selectTraceExpressions: List<Expression> = Collections.unmodifiableList(_selectExpressions[Scope.Trace]!!)

    /**
     * The expressions to select on the event scope.
     */
    val selectEventExpressions: List<Expression> = Collections.unmodifiableList(_selectExpressions[Scope.Event]!!)

    // endregion
    // region where clause
    /**
     * The expression in the where clause.
     */
    var whereExpression: Expression = Expression.empty
        private set

    // endregion

    // region group by clause
    // However PQL does not allow for many group by clauses, as of version 0.1, I expect that the support will be
    // added in a future version. So the below collections hold separate sets of attributes for each scope.
    private val _groupByStandardAttributes: Map<Scope, LinkedHashSet<Attribute>> = mapOf(
        Scope.Log to LinkedHashSet(),
        Scope.Trace to LinkedHashSet(),
        Scope.Event to LinkedHashSet()
    )
    private val _groupByOtherAttributes: Map<Scope, LinkedHashSet<Attribute>> = mapOf(
        Scope.Log to LinkedHashSet(),
        Scope.Trace to LinkedHashSet(),
        Scope.Event to LinkedHashSet()
    )

    /**
     * The standard attributes used for grouping on the log scope.
     */
    val groupLogByStandardAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_groupByStandardAttributes[Scope.Log]!!)

    /**
     * The standard attributes used for grouping on the trace scope.
     */
    val groupTraceByStandardAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_groupByStandardAttributes[Scope.Trace]!!)

    /**
     * The standard attributes used for grouping on the event scope.
     */
    val groupEventByStandardAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_groupByStandardAttributes[Scope.Event]!!)

    /**
     * The non-standard attributes used for grouping on the log scope.
     */
    val groupLogByOtherAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_groupByOtherAttributes[Scope.Log]!!)

    /**
     * The non-standard attributes used for grouping on the trace scope.
     */
    val groupTraceByOtherAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_groupByOtherAttributes[Scope.Trace]!!)

    /**
     * The non-standard attributes used for grouping on the event scope.
     */
    val groupEventByOtherAttributes: Set<Attribute> =
        Collections.unmodifiableSet(_groupByOtherAttributes[Scope.Event]!!)

    /**
     * Indicates whether the implicit out-of-scope group by applies.
     */
    var isImplicitGroupBy: Boolean = false
        private set

    /**
     * Indicates whether the group by on log scope is used.
     */
    val isGroupLogBy: Boolean
        get() = _groupByStandardAttributes[Scope.Log]!!.size > 0 || _groupByOtherAttributes[Scope.Log]!!.size > 0

    /**
     * Indicates whether the group by on trace scope is used.
     */
    val isGroupTraceBy: Boolean
        get() = _groupByStandardAttributes[Scope.Trace]!!.size > 0 || _groupByOtherAttributes[Scope.Trace]!!.size > 0

    /**
     * Indicates whether the group by on event scope is used.
     */
    val isGroupEventBy: Boolean
        get() = _groupByStandardAttributes[Scope.Event]!!.size > 0 || _groupByOtherAttributes[Scope.Event]!!.size > 0

    // end region
    // endregion

    // region order by clause
    private val _orderByExpressions: Map<Scope, ArrayList<OrderedExpression>> = mapOf(
        Scope.Log to ArrayList(),
        Scope.Trace to ArrayList(),
        Scope.Event to ArrayList()
    )

    /**
     * The list of ordering expressions on log scope in decreasing precedence.
     */
    val orderByLogExpressions: List<OrderedExpression> =
        Collections.unmodifiableList(_orderByExpressions[Scope.Log]!!)

    /**
     * The list of ordering expressions on trace scope in decreasing precedence.
     */
    val orderByTraceExpressions: List<OrderedExpression> =
        Collections.unmodifiableList(_orderByExpressions[Scope.Trace]!!)

    /**
     * The list of ordering expressions on event scope in decreasing precedence.
     */
    val orderByEventExpressions: List<OrderedExpression> =
        Collections.unmodifiableList(_orderByExpressions[Scope.Event]!!)
    // endregion

    init {
        val tree = parser.query()
        val walker = ParseTreeWalker()
        walker.walk(Listener(), tree)
        validateSelectAll()
        validateGroupByAttributes()
        if (errorListener.error !== null)
            throw errorListener.error!!
    }

    private fun validateSelectAll() {
        validateSelectAll(Scope.Log, _selectAllLog)
        validateSelectAll(Scope.Trace, _selectAllTrace)
        validateSelectAll(Scope.Event, _selectAllEvent)
    }

    private fun validateSelectAll(scope: Scope, flag: Boolean?) {
        if (!isImplicitSelectAll && flag == null)
            return

        val standard = _selectStandardAttributes[scope]!!
        val other = _selectOtherAttributes[scope]!!
        if (standard.isNotEmpty() || other.isNotEmpty()) {
            val first = standard.firstOrNull() ?: other.first()
            errorListener.emitWarning(
                IllegalArgumentException(
                    "Line ${first.line} position ${first.charPositionInLine}: Use of select all with scope $scope and "
                            + "referencing attributes by name on the same scope is meaningless. Attributes "
                            + "${(standard + other).joinToString(", ")} are removed from the select clause."
                )
            )
            standard.clear()
            other.clear()
        }
    }


    private fun validateGroupByAttributes() {
        validateExplicitGroupBy(_selectStandardAttributes, _groupByStandardAttributes)
        validateExplicitGroupBy(_selectOtherAttributes, _groupByOtherAttributes)
        val groupByAttributes = _groupByStandardAttributes.mapValues {
            LinkedHashSet<Attribute>(it.value).apply { addAll(_groupByOtherAttributes[it.key]!!) }
        }
        validateExplicitGroupBy(_selectExpressions, groupByAttributes)

        val orderByExpressions = _orderByExpressions.mapValues {
            sequence {
                it.value.forEach { yield(it.base) }
            }.asIterable()
        }
        validateExplicitGroupBy(orderByExpressions, groupByAttributes)

        if (!isGroupLogBy && !isGroupTraceBy && !isGroupEventBy) {
            // possible implicit group by
            val selectAllExpressions = sequence {
                _selectStandardAttributes.values.forEach { yieldAll(it) }
                _selectOtherAttributes.values.forEach { yieldAll(it) }
                _selectExpressions.values.forEach { yieldAll(it) }
            }.asIterable()
            validateImplicitGroupBy(selectAllExpressions)

            val orderByAllExpressions = sequence {
                _orderByExpressions.values.forEach { it.forEach { yield(it.base) } }
            }.asIterable()
            validateImplicitGroupBy(orderByAllExpressions)
        }
    }

    private fun validateExplicitGroupBy(
        toValidate: Map<Scope, Iterable<Expression>>,
        groupByMap: Map<Scope, Set<Attribute>>
    ) =
        toValidate
            .flatMap { it.value }
            .flatMap {
                it.filterRecursively { it !is Function || it.type != FunctionType.Aggregation }
                    .filterIsInstance<Attribute>()
                    .asIterable()
            }
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
                        "Line ${it.line} position ${it.charPositionInLine}: The attribute $it is not included in the group by clause. Such attributes can be used only as an argument of an aggregation function."
                    )
                )
            }

    private fun validateImplicitGroupBy(toValidate: Iterable<Expression>) {
        val anyAggregation = toValidate
            .any { it.filter { it is Function && it.type == FunctionType.Aggregation }.any() }

        if (anyAggregation) {
            // implicit group by for sure
            isImplicitGroupBy = true
            isImplicitSelectAll = false
            if (_selectAllLog == true || _selectAllTrace == true || _selectAllEvent == true) {
                // query uses explicit select all
                errorListener.delayedThrow(
                    IllegalArgumentException(
                        "Use of the explicit select all clause with the implicit group by clause is meaningless."
                    )
                )
            }

            val orderByExpression = _orderByExpressions.values.flatten().firstOrNull()
            if (orderByExpression !== null) {
                errorListener.emitWarning(
                    IllegalArgumentException(
                        "Line ${orderByExpression.line} position ${orderByExpression.charPositionInLine}: Use of the order "
                                + "by clause with the implicit group by clause is meaningless. The order by clause is removed."
                    )
                )
                _orderByExpressions.values.forEach { it.clear() }
            }

            val nonaggregated = toValidate
                .flatMap {
                    it.filterRecursively { it !is Function || it.type != FunctionType.Aggregation }
                        .filterIsInstance<Attribute>()
                        .asIterable()
                }
            if (nonaggregated.any()) {
                // nonaggregated attributes exist
                errorListener.delayedThrow(
                    IllegalArgumentException(
                        "Use of an aggregation function without a group by clause requires all attributes to be aggregated. "
                                + "The attributes ${nonaggregated.joinToString(", ")} are not supplied to an aggregation function."
                    )
                )
            }
        }
    }

    override fun toString(): String = query


    private inner class Listener : QueryLanguageBaseListener() {
        override fun exitSelect_all_implicit(ctx: QueryLanguage.Select_all_implicitContext?) {
            isImplicitSelectAll = true
        }

        override fun exitSelect_all(ctx: QueryLanguage.Select_allContext?) {
            _selectAllLog = true
            _selectAllTrace = true
            _selectAllEvent = true
        }

        override fun enterScoped_select_all(ctx: QueryLanguage.Scoped_select_allContext?) {
            val token = ctx!!.SCOPE()
            val scope = Scope.parse(token.text)
            when (scope) {
                Scope.Log -> _selectAllLog = true
                Scope.Trace -> _selectAllTrace = true
                Scope.Event -> _selectAllEvent = true
            }
        }

        override fun exitArith_expr_root(ctx: QueryLanguage.Arith_expr_rootContext?) {
            val expression = parseExpression(ctx!!)
            validateHoisting(expression)

            if (expression is Attribute) {
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
            whereExpression = parseExpression(ctx!!.children[1])
            val aggregation = whereExpression
                .filter { it is Function && it.type == FunctionType.Aggregation }
                .firstOrNull()
            if (aggregation !== null)
                errorListener.delayedThrow(
                    IllegalArgumentException(
                        "Line ${aggregation.line} position ${aggregation.charPositionInLine}: The aggregation function call is not supported in the where clause."
                    )
                )
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
                .map { Attribute(it.text, it.line, it.charPositionInLine) }
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

        // region PQL order by clause
        override fun exitOrdered_expression_root(ctx: QueryLanguage.Ordered_expression_rootContext?) {
            val expression = parseExpression(ctx!!.arith_expr())
            validateHoisting(expression)

            val order = OrderDirection.parse(ctx.order_dir().text)
            _orderByExpressions[expression.effectiveScope]!!.add(OrderedExpression(expression, order))
        }
        // endregion

        private fun validateHoisting(expression: Expression) {
            val hoisted = expression
                .filterRecursively { it !is Function || it.type != FunctionType.Aggregation }
                .filter { it is Attribute && it.hoistingPrefix.isNotEmpty() }.firstOrNull()

            if (hoisted !== null)
                errorListener.delayedThrow(
                    IllegalArgumentException(
                        "Line ${hoisted.line} position ${hoisted.charPositionInLine}: Scope hoisting is not supported in the select and the order by clauses, except in an aggregate function."
                    )
                )
        }

        private fun parseExpression(ctx: ParseTree): Expression {
            if (ctx.childCount == 0) {
                // terminal
                return parseToken(ctx.payload as Token)
            }
            val token = ctx.getChild(0).payload as? Token
            with(token) {
                return when (this?.type) {
                    QueryLanguage.FUNC_SCALAR0 -> Function(text, line, charPositionInLine)
                    QueryLanguage.FUNC_SCALAR1, QueryLanguage.FUNC_AGGR -> Function(
                        text,
                        line,
                        charPositionInLine,
                        parseExpression(ctx.getChild(2))
                    )
                    else -> {
                        val array =
                            (0 until ctx.childCount).map { parseExpression(ctx.getChild(it)) }.toTypedArray()
                        if (array.size == 1) array[0] else Expression(*array)
                    }
                }
            }
        }

        private fun parseToken(token: Token): Expression = try {
            when (token.type) {
                QueryLanguage.ID -> Attribute(token.text, token.line, token.charPositionInLine)
                QueryLanguage.BOOLEAN -> BooleanLiteral(token.text, token.line, token.charPositionInLine)
                QueryLanguage.NUMBER -> NumberLiteral(token.text, token.line, token.charPositionInLine)
                QueryLanguage.DATETIME -> DateTimeLiteral(token.text, token.line, token.charPositionInLine)
                QueryLanguage.STRING -> StringLiteral(token.text, token.line, token.charPositionInLine)
                QueryLanguage.NULL -> NullLiteral(token.text, token.line, token.charPositionInLine)
                else -> Operator(token.text, token.line, token.charPositionInLine)
            }
        } catch (e: Exception) {
            errorListener.delayedThrow(e)
            Expression.empty
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