lexer grammar QLLexer;
tokens {STRING}
// lexer rules start with uppercase letters

SELECT      : 'select' ;
WHERE       : 'where' ;
GROUP_BY    : 'group by' ;
ORDER_BY    : 'order by' ;
LIMIT       : 'limit' ;
OFFSET      : 'offset' ;

STRING_SINGLE : '\'' ( '\\\'' | . )*? '\'' -> type(STRING) ;
STRING_DOUBLE : '"' ( '\\"' | . )*? '"' -> type(STRING) ;

NUMBER      : '-'? INT ('.' [0-9] +)? EXP? ;
BOOLEAN     : 'true' | 'false' ;
DATE        : 'date' ;
TIMEOFDAY   : 'timeofday' ;
DATETIME    : 'datetime' ;

FUNC_AGGR   : 'min'
            | 'max'
            | 'avg'
            | 'count'
            | 'sum'
            ;

FUNC_SCALAR : 'year'
            | 'month'
            | 'day'
            | 'hour'
            | 'minute'
            | 'second'
            | 'millisecond'
            | 'quarter'
            | 'dayOfWeek'
            | 'now'
            | 'dateDiff'
            | 'toDate'
            | 'upper'
            | 'lower'
            ;

OP_MUL      : '*' ;
OP_DIV      : '/' ;
OP_ADD      : '+' ;
OP_SUB      : '-' ;

OP_LT       : '<' ;
OP_LE       : '<=' ;
OP_GT       : '>' ;
OP_GE       : '>=' ;
OP_EQ       : '=' ;
OP_NEQ      : '!='
            | '<>'
            ;
OP_IS_NULL    : 'is null' ;
OP_IS_NOT_NULL: 'is not null' ;

OP_AND      : 'and' ;
OP_OR       : 'or' ;
OP_NOT      : 'not' ;

OP_CONTAINS : 'contains' ;
OP_STARTS_WITH: 'starts' [ \t\r\n]+ 'with' ;
OP_ENDS_WITH: 'ends' [ \t\r\n]+ 'with' ;
OP_MATCHES  : 'matches' ;
OP_LIKE     : 'like' ;

L_PARENTHESIS : '(' ;
R_PARENTHESIS : ')' ;
COMMA       : ',' ;
ORDER_ASC   : 'asc' ;
ORDER_DESC  : 'desc' ;

ID          : LETTER (LETTER|DIGIT|':')*
            | '[' ('\\`' | .)*? ']';

WS          : [ \t\r\n]+ -> skip ; // skip spaces, tabs, newlines, \r (Windows)

fragment INT    : '0' | [1-9] DIGIT* ;
fragment EXP    : [Ee] [+\-]? INT ;
fragment LETTER : [a-zA-Z\u0080-\u00FF_] ;
fragment DIGIT  : [0-9] ;


