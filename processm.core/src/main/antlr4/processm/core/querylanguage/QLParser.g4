parser grammar QLParser;
options { tokenVocab=QLLexer; }
// Process Query Language
// See https://git.processtom.com/processm-team/processm/-/wikis/Process-Query-Language for language details.

// parser rules start with lowercase letters
// Axiom.
query       : select where? group_by? order_by? limit? offset? EOF
            ;

select      :                               # select_all_implicit
            | SELECT '*' (',' column_list)? # select_all
            | SELECT column_list            # select_column_list
            ;

where       : WHERE logic_expr ;

group_by    : GROUP BY id_list          # group_trace_by
            | GROUP SCOPE BY id_list    # group_scope_by
            ;

order_by    : ORDER_BY column_list_with_order
            ;

limit       : LIMIT limit_number (',' limit_number)*;

offset      : OFFSET offset_number (',' offset_number)*;

column_list : SCOPE COLON '*'                   # scoped_select_all
            | SCOPE COLON '*' ',' column_list   # scoped_select_all
            | arith_expr_root                   # column_list_arith_expr_root
            | arith_expr_root ',' column_list   # column_list_arith_expr_root
            ;

arith_expr_root : arith_expr ;

id_list     : ID (',' ID)* ;

column_list_with_order : ordered_expression_root (',' ordered_expression_root)* ;

ordered_expression_root : arith_expr order_dir ;

order_dir   :
            | ORDER_ASC
            | ORDER_DESC
            ;

limit_number: NUMBER ;
offset_number: NUMBER ;

scalar      : STRING
            | NUMBER
            | BOOLEAN
            | DATETIME
            | NULL
            ;

// The order of productions reflects the operator precedence
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

// The order of productions reflects the operator precedence
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
