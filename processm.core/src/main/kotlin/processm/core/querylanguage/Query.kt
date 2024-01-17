package processm.core.querylanguage

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.util.*
import kotlin.math.round

/**
 * Represents log query as parsed from the string given as constructor argument.
 * @property query The string representation of the query.
 * @throws RecognitionException
 * @throws IllegalArgumentException
 */
@Suppress("MapGetWithNotNullAssertionOperator")
class Query(val query: String) {

    /**
     * This constructor is provided for backward-compatibility with the previous implementation of XES layer and its
     * use is discouraged in new code.
     * @param logId is the database id of the log. Not to be confused with log:identity:id.
     */
    @Deprecated("Use the primary constructor.", level = DeprecationLevel.WARNING)
    constructor(logId: Int) : this("where log:db:id=$logId")

    // region parser
    private val errorListener: ErrorListener = ErrorListener()
    // endregion

    // region data model
    /**
     * The first warning emitted during parsing of this query. Subsequent warnings are stored as suppressed exceptions.
     * The null value refers to no warnings.
     *
     * An exception is considered a warning, if its cause does not change semantics of the query, and so the query
     * yields the same results with and without this cause.
     *
     * @see Throwable.getSuppressed
     */
    val warning: Exception?
        get() = errorListener.warning

    // region select clause
    private val _isImplicitSelectAll: EnumMap<Scope, Boolean> = EnumMap<Scope, Boolean>(Scope::class.java).apply {
        put(Scope.Log, false)
        put(Scope.Trace, false)
        put(Scope.Event, false)
    }

    /**
     * Whether the select all clause is not specified explicitly, but it is implied by the query structure.
     */
    val isImplicitSelectAll: Map<Scope, Boolean> = Collections.unmodifiableMap(_isImplicitSelectAll)

    private val _selectAll: MutableMap<Scope, Boolean?> = EnumMap<Scope, Boolean>(Scope::class.java)

    /**
     * Indicates whether to select all (standard and non-standard) attributes on particular scopes.
     */
    val selectAll: Map<Scope, Boolean?> = object : Map<Scope, Boolean?> by _selectAll {
        override fun get(key: Scope): Boolean = _selectAll[key] ?: _isImplicitSelectAll[key]!!
    }

    private val _selectStandardAttributes: Map<Scope, LinkedHashSet<Attribute>> =
        EnumMap<Scope, LinkedHashSet<Attribute>>(Scope::class.java).apply {
            put(Scope.Log, LinkedHashSet())
            put(Scope.Trace, LinkedHashSet())
            put(Scope.Event, LinkedHashSet())
        }

    private val _selectOtherAttributes: Map<Scope, LinkedHashSet<Attribute>> =
        EnumMap<Scope, LinkedHashSet<Attribute>>(Scope::class.java).apply {
            put(Scope.Log, LinkedHashSet())
            put(Scope.Trace, LinkedHashSet())
            put(Scope.Event, LinkedHashSet())
        }

    private val _selectExpressions: Map<Scope, ArrayList<Expression>> =
        EnumMap<Scope, ArrayList<Expression>>(Scope::class.java).apply {
            put(Scope.Log, ArrayList())
            put(Scope.Trace, ArrayList())
            put(Scope.Event, ArrayList())
        }

    /**
     * The standard attributes to select split into the scopes.
     */
    val selectStandardAttributes: Map<Scope, Set<Attribute>> = Collections.unmodifiableMap(_selectStandardAttributes)

    /**
     * The non-standard attributes to select split into the scopes.
     */
    val selectOtherAttributes: Map<Scope, Set<Attribute>> = Collections.unmodifiableMap(_selectOtherAttributes)

    /**
     * The expressions to select split into the scopes.
     */
    val selectExpressions: Map<Scope, List<Expression>> = Collections.unmodifiableMap(_selectExpressions)
    // endregion

    // region delete clause
    /**
     * The scope to delete the objects at. Null for non-deleting query.
     */
    var deleteScope: Scope? = null
        private set
    // endregion

    // region where clause
    /**
     * The expression in the where clause.
     */
    var whereExpression: Expression = Expression.empty
        private set

    // endregion

    // region group by clause
    private val _isImplicitGroupBy: EnumMap<Scope, Boolean> = EnumMap<Scope, Boolean>(Scope::class.java).apply {
        put(Scope.Log, false)
        put(Scope.Trace, false)
        put(Scope.Event, false)
    }
    private val _groupByStandardAttributes: Map<Scope, LinkedHashSet<Attribute>> =
        EnumMap<Scope, LinkedHashSet<Attribute>>(Scope::class.java).apply {
            put(Scope.Log, LinkedHashSet())
            put(Scope.Trace, LinkedHashSet())
            put(Scope.Event, LinkedHashSet())
        }

    private val _groupByOtherAttributes: Map<Scope, LinkedHashSet<Attribute>> =
        EnumMap<Scope, LinkedHashSet<Attribute>>(Scope::class.java).apply {
            put(Scope.Log, LinkedHashSet())
            put(Scope.Trace, LinkedHashSet())
            put(Scope.Event, LinkedHashSet())
        }

    /**
     * The standard attributes used for grouping on particular scopes.
     */
    val groupByStandardAttributes: Map<Scope, Set<Attribute>> = Collections.unmodifiableMap(_groupByStandardAttributes)

    /**
     * The non-standard attributes used for grouping on particular scopes.
     */
    val groupByOtherAttributes: Map<Scope, Set<Attribute>> = Collections.unmodifiableMap(_groupByOtherAttributes)

    /**
     * Indicates whether the implicit out-of-scope group by applies.
     */
    var isImplicitGroupBy: Map<Scope, Boolean> = Collections.unmodifiableMap(_isImplicitGroupBy)

    /**
     * Indicates whether the group by clause occurs on particular scopes.
     */
    val isGroupBy: Map<Scope, Boolean> = object : Map<Scope, Boolean> by emptyMap() {
        override fun get(scope: Scope): Boolean =
            _groupByStandardAttributes[scope]!!.size > 0 || _groupByOtherAttributes[scope]!!.size > 0
    }

    // end region
    // endregion

    // region order by clause
    private val _orderByExpressions: Map<Scope, ArrayList<OrderedExpression>> =
        EnumMap<Scope, ArrayList<OrderedExpression>>(Scope::class.java).apply {
            put(Scope.Log, ArrayList())
            put(Scope.Trace, ArrayList())
            put(Scope.Event, ArrayList())
        }

    /**
     * The lists of ordering expressions in decreasing precedence on particular scopes.
     */
    val orderByExpressions: Map<Scope, List<OrderedExpression>> = Collections.unmodifiableMap(_orderByExpressions)
    // endregion

    // region limit clause
    private val _limit: MutableMap<Scope, Long> = EnumMap(Scope::class.java)

    /**
     * The maximum number of logs, traces, and events, respectively, returned by this query. null mean no limit.
     */
    val limit: Map<Scope, Long> = Collections.unmodifiableMap(_limit)
    // endregion

    // region offset clause
    private val _offset: MutableMap<Scope, Long> = EnumMap(Scope::class.java)

    /**
     * The offset number of the first log, trace, event returned by this query. null means no offset.
     */
    val offset: Map<Scope, Long> = Collections.unmodifiableMap(_offset)
    // endregion

    // region actual parsing
    init {
        val stream: CodePointCharStream = CharStreams.fromString(query)
        val lexer = QLLexer(stream)
        val tokens = CommonTokenStream(lexer)
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        val parser = QLParser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        val tree = parser.query()
        val walker = ParseTreeWalker()
        walker.walk(Listener(tokens), tree)
        validateSelectAll()
        validateGroupByAttributes()
        if (errorListener.error !== null)
            throw errorListener.error!!
    }
    // endregion

    /**
     * Sets the limits on the numbers of [log]s, [trace]s, and [event]s returned by this query. For query containing a
     * limit, this method calculates the minimum of that limit and the passed value. For query not containing a limit,
     * this method imposes an upper bound of the passed value. Pass null to skip the application of a particular limit.
     *
     * @param log The upper bound on the total number of logs returned, or null to not impose the limit.
     * @param trace The upper bound on the number of traces returned per log, or null to not impose the limit.
     * @param evemt The upper bound on the number of events returned per trace, or null to not impose the limit.
     */
    fun applyLimits(log: Long? = null, trace: Long? = null, event: Long? = null) {
        log?.let { applyLimit(Scope.Log, it) }
        trace?.let { applyLimit(Scope.Trace, it) }
        event?.let { applyLimit(Scope.Event, it) }
    }

    private fun applyLimit(scope: Scope, value: Long) =
        _limit.compute(scope) { _, old -> (old ?: Long.MAX_VALUE).coerceAtMost(value) }

    private fun validateSelectAll() {
        validateSelectAll(Scope.Log, _selectAll[Scope.Log])
        validateSelectAll(Scope.Trace, _selectAll[Scope.Trace])
        validateSelectAll(Scope.Event, _selectAll[Scope.Event])
    }

    private fun validateSelectAll(scope: Scope, flag: Boolean?) {
        if (!isImplicitSelectAll[scope]!! && flag == null)
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
        // replace implicit select all with grouping attributes
        for (scope in _isImplicitSelectAll.filter { it.value && isGroupBy[it.key]!! }.map { it.key }) {
            var currentOrLowerScope: Scope? = scope
            do {
                _isImplicitSelectAll[currentOrLowerScope] = false
                currentOrLowerScope = currentOrLowerScope!!.lower
            } while (currentOrLowerScope !== null)
            _groupByStandardAttributes[scope]!!.forEach { _selectStandardAttributes[it.scope]!!.add(it.dropHoisting()) }
            _groupByOtherAttributes[scope]!!.forEach { _selectOtherAttributes[it.scope]!!.add(it.dropHoisting()) }
        }

        validateExplicitGroupBy(_selectStandardAttributes, _groupByStandardAttributes)
        validateExplicitGroupBy(_selectOtherAttributes, _groupByOtherAttributes)
        val groupByAttributes = _groupByStandardAttributes.mapValues {
            LinkedHashSet<Attribute>(it.value).apply { addAll(_groupByOtherAttributes[it.key]!!) }
        }
        validateExplicitGroupBy(_selectExpressions, groupByAttributes)

        for (_scope in _selectAll.filterValues { it ?: false }.keys) {
            var scope = _scope
            while (true) {
                if (isGroupBy[scope]!!) {
                    errorListener.delayedThrow(
                        IllegalArgumentException(
                            "Select all clause at scope $_scope is not allowed together with the group by clause at scope $scope."
                        )
                    )
                }
                scope = scope.upper ?: break
            }
        }

        val orderByExpressions = _orderByExpressions.mapValues {
            sequence {
                it.value.forEach { yield(it.base) }
            }.asIterable()
        }
        validateExplicitGroupBy(orderByExpressions, groupByAttributes)

        if (isGroupBy[Scope.Log] == false || isGroupBy[Scope.Trace] == false || isGroupBy[Scope.Event] == false) {
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

        assert(deleteScope === null || isGroupBy.values.all { !it } && isImplicitGroupBy.values.all { !it }) {
            "Combining the deletion with the group by clause should not be possible due to the grammar"
        }
    }

    private fun validateExplicitGroupBy(
        toValidate: Map<Scope, Iterable<Expression>>,
        groupByMap: Map<Scope, Set<Attribute>>
    ) =
        toValidate
            .flatMap { it.value }
            .flatMap {
                it.filterRecursively { it !is Function || it.functionType != FunctionType.Aggregation }
                    .filterIsInstance<Attribute>()
                    .asIterable()
            }
            .filter {
                var groupByEnabled = false
                var scope: Scope? = it.scope
                do {
                    if (_groupByStandardAttributes[scope]!!.size != 0 || _groupByOtherAttributes[scope]!!.size != 0) {
                        groupByEnabled = true
                        // FIXME: the below check is done in O(n) time; replace it with a O(1) function
                        // Ignore hoisting in the below check
                        if (groupByMap[scope]!!.any { inMap ->
                                it.scope == inMap.scope && (it.isStandard && it.standardName == inMap.standardName || !it.isStandard && it.name == inMap.name)
                            })
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
        val scopesWithAggregation = EnumSet.noneOf(Scope::class.java)
        for (expr in toValidate) {
            scopesWithAggregation.addAll(
                expr
                    .filter { it is Function && it.functionType == FunctionType.Aggregation }
                    .map { it.effectiveScope }
                    .filterNot { isGroupBy[it]!! } // exclude active group by clauses
            )
        }

        for (scope in scopesWithAggregation) {
            // implicit group by for sure
            _isImplicitGroupBy[scope] = true
            _isImplicitSelectAll[scope] = false

            val orderByExpression = _orderByExpressions[scope]!!.firstOrNull()
            if (orderByExpression !== null) {
                errorListener.emitWarning(
                    IllegalArgumentException(
                        "Line ${orderByExpression.line} position ${orderByExpression.charPositionInLine}: Use of the order "
                                + "by clause with the implicit group by clause is meaningless. The order by clause is removed."
                    )
                )
                _orderByExpressions[scope]!!.clear()
            }


            var currentOrLowerScope = scope
            do {
                if (_selectAll[currentOrLowerScope] == true) {
                    // query uses scoped select all or explicit select all
                    errorListener.delayedThrow(
                        IllegalArgumentException(
                            "Use of the explicit select all clause with the implicit group by clause is meaningless."
                        )
                    )
                }

                val nonaggregated = toValidate
                    .flatMap {
                        it.filterRecursively { it !is Function || it.functionType != FunctionType.Aggregation }
                            .filterIsInstance<Attribute>()
                            .filter { it.effectiveScope == scope }
                            .asIterable()
                    }
                if (nonaggregated.any()) {
                    // nonaggregated attributes exist
                    errorListener.delayedThrow(
                        IllegalArgumentException(
                            "Use of an aggregation function without a group by clause requires all attributes on the same and the lower scopes to be aggregated. "
                                    + "The attribute(s) ${nonaggregated.joinToString(", ")} is/are not supplied to an aggregation function."
                        )
                    )
                }

                currentOrLowerScope = currentOrLowerScope.lower
            } while (currentOrLowerScope != null)
        }
    }

    override fun toString(): String = query


    private inner class Listener(val tokens: CommonTokenStream) : QLParserBaseListener() {
        override fun exitSelect_all_implicit(ctx: QLParser.Select_all_implicitContext?) {
            _isImplicitSelectAll[Scope.Log] = true
            _isImplicitSelectAll[Scope.Trace] = true
            _isImplicitSelectAll[Scope.Event] = true
        }

        override fun exitSelect_all(ctx: QLParser.Select_allContext?) {
            _selectAll[Scope.Log] = true
            _selectAll[Scope.Trace] = true
            _selectAll[Scope.Event] = true
        }

        override fun enterScoped_select_all(ctx: QLParser.Scoped_select_allContext?) {
            val token = ctx!!.SCOPE()
            val scope = Scope.parse(token.text)
            _selectAll[scope] = true
        }

        override fun exitArith_expr_root(ctx: QLParser.Arith_expr_rootContext?) {
            val expression = parseExpression(ctx!!)
            validateHoistingInSelectAndOrderBy(expression)

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

        override fun exitDelete(ctx: QLParser.DeleteContext?) {
            deleteScope = ctx!!.SCOPE()?.let { Scope.parse(it.text) } ?: Scope.Event
            exitSelect_all_implicit(null) // mark all attributes for selection
        }

        // region PQL where clause
        override fun exitWhere(ctx: QLParser.WhereContext?) {
            whereExpression = parseExpression(ctx!!.children[1])
            val aggregation = whereExpression
                .filter { it is Function && it.functionType == FunctionType.Aggregation }
                .firstOrNull()
            if (aggregation !== null)
                errorListener.delayedThrow(
                    IllegalArgumentException(
                        "Line ${aggregation.line} position ${aggregation.charPositionInLine}: The aggregation function call is not supported in the where clause."
                    )
                )


            val classifier = whereExpression.filter { it is Attribute && it.isClassifier }.firstOrNull()
            if (classifier !== null)
                errorListener.delayedThrow(
                    IllegalArgumentException(
                        "Line ${classifier.line} position ${classifier.charPositionInLine}: The use of the classifier is not supported in the where clause."
                    )
                )
        }
        // endregion

        // region PQL group by clause

        override fun exitGroup_by(ctx: QLParser.Group_byContext?) {
            handleGroupByIdList(ctx!!.id_list().sourceInterval)
        }

        private fun handleGroupByIdList(interval: Interval) {
            tokens.get(interval.a, interval.b)
                .filter { it.type != QLParser.COMMA }
                .map { parseToken(it) }
                .filterIsInstance<Attribute>() // May be Expression.empty if the constructor of Attribute throws an exception
                .forEach {
                    (if (it.isStandard) _groupByStandardAttributes else _groupByOtherAttributes)[it.effectiveScope]!!
                        .add(it)
                }
        }


        // endregion

        // region PQL order by clause
        override fun exitOrdered_expression_root(ctx: QLParser.Ordered_expression_rootContext?) {
            val expression = parseExpression(ctx!!.arith_expr())
            validateHoistingInSelectAndOrderBy(expression)

            val order = OrderDirection.parse(ctx.order_dir().text)
            _orderByExpressions[expression.effectiveScope]!!.add(OrderedExpression(expression, order))
        }
        // endregion

        // region PQL limit & offset clauses

        override fun exitLimit_number(ctx: QLParser.Limit_numberContext?) {
            parseLimitOrOffsetNumber(ctx!!.getChild(0).payload as Token, "limit") { scope, value, number ->
                fun warn() = errorListener.emitWarning(
                    IllegalArgumentException(
                        "Line ${number.line} position ${number.charPositionInLine}: A duplicate limit overrides the previous value."
                    )
                )

                if (_limit[scope] !== null)
                    warn()
                _limit[scope] = value
            }
        }

        override fun exitOffset_number(ctx: QLParser.Offset_numberContext?) {
            parseLimitOrOffsetNumber(ctx!!.getChild(0).payload as Token, "offset") { scope, value, number ->
                fun warn() = errorListener.emitWarning(
                    IllegalArgumentException(
                        "Line ${number.line} position ${number.charPositionInLine}: A duplicate offset overrides the previous value."
                    )
                )

                if (_offset[scope] !== null)
                    warn()
                _offset[scope] = value
            }
        }

        private fun parseLimitOrOffsetNumber(
            token: Token,
            clause: String,
            setter: (scope: Scope, value: Long, number: NumberLiteral) -> Unit
        ) {
            val number = parseToken(token) as? NumberLiteral ?: return
            val intValue = round(number.value)

            if (intValue <= 0.0 || number.value.isNaN() || number.value.isInfinite())
                errorListener.delayedThrow(
                    IllegalArgumentException(
                        "Line ${number.line} position ${number.charPositionInLine}: A value of the $clause must be a positive integer, $number given."
                    )
                )

            if (number.value != intValue)
                errorListener.emitWarning(
                    IllegalArgumentException(
                        "Line ${number.line} position ${number.charPositionInLine}: Dropped the decimal part of $number."
                    )
                )

            if (number.scope === null) {
                errorListener.delayedThrow(
                    IllegalArgumentException(
                        "Line ${number.line} position ${number.charPositionInLine}: Scope is required for the number $number in the $clause clause."
                    )
                )
            } else {
                setter(number.scope, intValue.toLong(), number)
            }
        }

        // endregion

        private fun validateHoistingInSelectAndOrderBy(expression: Expression) {
            val hoisted = expression
                .filterRecursively { it !is Function || it.functionType != FunctionType.Aggregation }
                .filter { it is Attribute && it.hoistingPrefix.isNotEmpty() }.firstOrNull()

            if (hoisted !== null)
                errorListener.delayedThrow(
                    IllegalArgumentException(
                        "Line ${hoisted.line} position ${hoisted.charPositionInLine}: Scope hoisting is not supported in the select and the order by clauses, except in an aggregation function."
                    )
                )
        }

        private fun validateTypes(expression: IExpression) {
            assert(expression.filter { it.type == Type.Any }.firstOrNull() === null)
            assert(expression.filter { it.expectedChildrenTypes.any { it == Type.Unknown } }.firstOrNull() === null)

            for (i in expression.children.indices) {

                if (expression.expectedChildrenTypes[i] != Type.Any && expression.children[i].type != Type.Unknown /* determined at run time */) {
                    if (expression.expectedChildrenTypes[i] != expression.children[i].type) {
                        errorListener.delayedThrow(
                            IllegalArgumentException(
                                "Line ${expression.line} position ${expression.charPositionInLine}: Expected ${expression.expectedChildrenTypes[i]} but ${expression.children[i].type} found."
                            )
                        )
                    }
                }

                validateTypes(expression.children[i])
            }
        }

        private fun parseExpression(ctx: ParseTree): Expression = parseExpressionInternal(ctx).also {
            validateTypes(it)
        }

        private fun parseExpressionInternal(ctx: ParseTree): Expression {
            if (ctx.childCount == 0) {
                // terminal
                val token = ctx.payload as? Token ?: return Expression.empty
                return parseToken(token)
            }
            val token = (0 until ctx.childCount)
                .map { it to (ctx.getChild(it).payload as? Token) }
                .firstOrNull { it.second !== null }
            with(token?.second) {
                return when (this?.type) {
                    QLParser.FUNC_SCALAR0 -> Function(text, line, charPositionInLine)
                    QLParser.FUNC_SCALAR1, QLParser.FUNC_AGGR -> Function(
                        text,
                        line,
                        charPositionInLine,
                        parseExpressionInternal(ctx.getChild(2))
                    )
                    QLParser.OP_ADD, QLParser.OP_SUB,
                    QLParser.OP_MUL, QLParser.OP_DIV,
                    QLParser.OP_AND, QLParser.OP_OR, QLParser.OP_NOT,
                    QLParser.OP_EQ, QLParser.OP_NEQ, QLParser.OP_GE, QLParser.OP_GT, QLParser.OP_LE, QLParser.OP_LT,
                    QLParser.OP_LIKE, QLParser.OP_MATCHES,
                    QLParser.OP_IN, QLParser.OP_NOT_IN,
                    QLParser.OP_IS_NULL, QLParser.OP_IS_NOT_NULL -> Operator(
                        text,
                        line,
                        charPositionInLine,
                        *Array(ctx.childCount - 1) {
                            parseExpressionInternal(ctx.getChild(if (it < token!!.first) it else it + 1))
                        })
                    else -> {
                        if (ctx.childCount == 1) parseExpressionInternal(ctx.getChild(0))
                        else Expression(*Array(ctx.childCount) { parseExpressionInternal(ctx.getChild(it)) })
                    }
                }
            }
        }

        private fun parseToken(token: Token): Expression = try {
            when (token.type) {
                QLParser.ID -> Attribute(token.text, token.line, token.charPositionInLine)
                QLParser.BOOLEAN -> BooleanLiteral(token.text, token.line, token.charPositionInLine)
                QLParser.NUMBER -> NumberLiteral(token.text, token.line, token.charPositionInLine)
                QLParser.DATETIME -> DateTimeLiteral(token.text, token.line, token.charPositionInLine)
                QLParser.UUID -> UUIDLiteral(token.text, token.line, token.charPositionInLine)
                QLParser.STRING -> StringLiteral(token.text, token.line, token.charPositionInLine)
                QLParser.NULL -> NullLiteral(token.text, token.line, token.charPositionInLine)
                else -> AnyExpression(token.text, token.line, token.charPositionInLine)
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
            val eWithMessage = RecognitionException(
                "Line $line position $charPositionInLine: $msg",
                recognizer,
                e?.inputStream,
                e?.ctx as ParserRuleContext?
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
