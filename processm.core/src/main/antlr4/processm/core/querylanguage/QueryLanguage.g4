parser grammar QueryLanguage;
options {tokenVocab=QLLexer;}

// parser rules start with lowercase letters
// Axiom. QL implements a subset of Google Visualization API Query Language limited to the below clauses.
// See https://developers.google.com/chart/interactive/docs/querylanguage for language details.
query       : select where group_by order_by limit offset EOF
            ;

select      :                       # select_implicit_all
            | SELECT '*'            # select_explicit_all
            | SELECT column_list    # select_column_list
            ;

where       :
            | WHERE logic_expr
            ;

group_by    :
            | GROUP_BY id_list
            ;

order_by    :
            | ORDER_BY column_list order_by_dir
            ;

order_by_dir:                   # order_by_asc
            | ORDER_ASC         # order_by_asc
            | ORDER_DESC        # order_by_desc
            ;

limit       :
            | LIMIT NUMBER ;

offset      :
            | OFFSET NUMBER ;

column_list : arith_expr
            | arith_expr ',' column_list
            ;

id_list     : ID
            | ID ',' id_list
            ;

scalar      : STRING
            | NUMBER
            | BOOLEAN
            | DATE STRING
            | TIMEOFDAY STRING
            | DATETIME STRING
            ;


// The order of productions reflects operator precedence
arith_expr  : '(' arith_expr ')'
            | arith_expr '*' arith_expr
            | arith_expr '/' arith_expr
            | arith_expr '+' arith_expr
            | arith_expr '-' arith_expr
            | func
            | ID
            | scalar
            ;

func        : FUNC_SCALAR '(' arith_expr ')'    # func_scalar
            | FUNC_AGGR '(' ID ')'              # func_aggr // Note: Aggregation functions can only take a column identifier as an argument
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

cmp_func    : OP_CONTAINS
            | OP_STARTS_WITH
            | OP_ENDS_WITH
            | OP_MATCHES
            | OP_LIKE
            ;

// The order of productions reflects operator precedence
logic_expr  : '(' logic_expr ')'
            | OP_NOT logic_expr
            | logic_expr OP_AND logic_expr
            | logic_expr OP_OR logic_expr
            | arith_expr cmp arith_expr
            | arith_expr cmp_null
            | arith_expr cmp_func arith_expr
            ;

