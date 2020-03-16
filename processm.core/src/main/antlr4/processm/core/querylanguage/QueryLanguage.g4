parser grammar QueryLanguage;
options {tokenVocab=QLLexer;}

// parser rules start with lowercase letters
// Axiom. QL implements a subset of Google Visualization API Query Language limited to the below clauses.
// See https://developers.google.com/chart/interactive/docs/querylanguage for language details.
query       : select where? group_by? order_by? limit? offset? EOF
            ;

select      :                               # select_all
            | SELECT '*' (',' column_list)? # select_all
            | SELECT column_list            # select_column_list
            ;

where       : WHERE logic_expr ;

group_by    : GROUP BY id_list          # group_trace_by
            | GROUP SCOPE BY id_list    # group_scope_by
            ;

order_by    : ORDER_BY column_list_with_order
            ;

limit       : LIMIT SCOPE COLON NUMBER (',' SCOPE COLON NUMBER)*;

offset      : OFFSET NUMBER ;

column_list : SCOPE COLON '*'                   # scoped_select_all
            | SCOPE COLON '*' ',' column_list   # scoped_select_all
            | arith_expr_root                   # column_list_arith_expr_root
            | arith_expr_root ',' column_list   # column_list_arith_expr_root
            ;

arith_expr_root : arith_expr ;

id_list     : ID (',' ID)* ;

column_list_with_order : arith_expr order_dir (',' arith_expr order_dir)* ;

order_dir:                      # order_by_asc
            | ORDER_ASC         # order_by_asc
            | ORDER_DESC        # order_by_desc
            ;

scalar      : STRING
            | NUMBER
            | BOOLEAN
            | DATETIME
            | NULL
            ;

// The order of productions reflects operator precedence
arith_expr  : '(' arith_expr ')'
            | arith_expr ('*' | '/') arith_expr
            | arith_expr ('+' | '-') arith_expr
            | func
            | ID
            | scalar
            ;

func        : FUNC_SCALAR0 '(' ')'
            | FUNC_SCALAR1 '(' arith_expr ')'
            | FUNC_AGGR '(' ID ')'              // Note: Aggregation functions can only take a column identifier as an argument
            ;

cmp         : OP_LT
            | OP_LE
            | OP_EQ
            | OP_NEQ
            | OP_GT
            | OP_GE
            ;

cmp_null    : OP_IS_NULL
            | OP_IS_NOT_NULL
            ;

cmp_func    : OP_MATCHES
            | OP_LIKE
            ;

cmp_in      : OP_IN
            | OP_NOT_IN
            ;

// The order of productions reflects operator precedence
logic_expr  : '(' logic_expr ')'
            | arith_expr cmp_in '(' id_or_scalar_list ')'
            | arith_expr cmp_func STRING
            | arith_expr cmp arith_expr
            | arith_expr cmp_null
            | OP_NOT logic_expr
            | logic_expr OP_AND logic_expr
            | logic_expr OP_OR logic_expr
            ;

id_or_scalar_list : (ID | scalar) (',' (ID | scalar))* ;
