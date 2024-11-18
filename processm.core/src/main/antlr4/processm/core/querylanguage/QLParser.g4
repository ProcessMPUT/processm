parser grammar QLParser;
options { tokenVocab=QLLexer; }
// Process Query Language
// See https://github.com/ProcessMPUT/processm/blob/master/docs/pql.md for language details.

// Parser rules start with lowercase letters
// Axiom.
query       : read_query EOF
            | delete_query EOF
            ;

read_query  : select where? group_by? order_by? limit? offset?
            ;

delete_query: delete where? order_by? limit? offset?
            ;

select      :                               # select_all_implicit
            | SELECT '*' (',' column_list)? # select_all
            | SELECT column_list            # select_column_list
            ;

delete      : DELETE SCOPE? ;

where       : WHERE logic_expr ;

group_by    : GROUP_BY id_list ;

order_by    : ORDER_BY column_list_with_order ;

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
            | UUID
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

// The order of productions reflects the operator precedence
logic_expr  : '(' logic_expr ')'
            | arith_expr (OP_IN | OP_NOT_IN) in_list
            | arith_expr (OP_MATCHES | OP_LIKE) STRING
            | arith_expr (OP_LT | OP_LE | OP_EQ | OP_NEQ | OP_GT | OP_GE) arith_expr
            | arith_expr (OP_IS_NULL | OP_IS_NOT_NULL)
            | OP_NOT logic_expr
            | logic_expr OP_AND logic_expr
            | logic_expr OP_OR logic_expr
            ;

in_list     : '(' id_or_scalar_list ')' ;

id_or_scalar_list : (ID | scalar) (',' (ID | scalar))* ;
