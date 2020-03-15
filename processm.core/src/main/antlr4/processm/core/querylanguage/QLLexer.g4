lexer grammar QLLexer;
tokens {STRING}
// lexer rules start with uppercase letters

SELECT      : 'select' ;
WHERE       : 'where' ;
GROUP       : 'group' ;
BY          : 'by' ;
ORDER_BY    : 'order' [ \t\r\n]+ 'by' ;
LIMIT       : 'limit' ;
OFFSET      : 'offset' ;

SCOPE       : 'log'
            | 'l'
            | 'trace'
            | 't'
            | 'event'
            | 'e'
            ;

STRING_SINGLE : (SCOPE ':')? '\'' ( '\\\'' | . )*? '\'' -> type(STRING) ;
STRING_DOUBLE : (SCOPE ':')? '"' ( '\\"' | . )*? '"' -> type(STRING) ;

NUMBER      : (SCOPE ':')? '-'? INT ('.' [0-9] +)? EXP? ;
BOOLEAN     : (SCOPE ':')? 'true' | 'false' ;
DATETIME    : (SCOPE ':')? 'D' ISODATE ('T' ISOTIME ISOTIMEZONE?)?
            | (SCOPE ':')? 'D' ISODATESHORT ('T'? ISOTIMESHORT ISOTIMEZONE?)?
            ;

NULL        : (SCOPE ':')? 'null' ;

FUNC_AGGR   : 'min'
            | 'max'
            | 'avg'
            | 'count'
            | 'sum'
            ;

FUNC_SCALAR1: 'date'
            | 'time'
            | 'year'
            | 'month'
            | 'day'
            | 'hour'
            | 'minute'
            | 'second'
            | 'millisecond'
            | 'quarter'
            | 'dayofweek'
            | 'upper'
            | 'lower'
            | 'round'
            ;

FUNC_SCALAR0: 'now'
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
            ;
OP_IS_NULL    : 'is' [ \t\r\n]+ 'null' ;
OP_IS_NOT_NULL: 'is' [ \t\r\n]+ 'not' [ \t\r\n]+ 'null' ;

OP_IN       : 'in';
OP_NOT_IN   : 'not' [ \t\r\n]+ 'in';

OP_AND      : 'and' ;
OP_OR       : 'or' ;
OP_NOT      : 'not' ;

OP_MATCHES  : 'matches' ;
OP_LIKE     : 'like' ;

L_PARENTHESIS : '(' ;
R_PARENTHESIS : ')' ;
COMMA       : ',' ;
COLON       : ':' ;
ORDER_ASC   : 'asc' ;
ORDER_DESC  : 'desc' ;

ID          : (SCOPE':')? ([a-zA-CE-Z_]|DIGIT)+ (':' (LETTER|DIGIT)+)?
            | '[' (SCOPE':')? ('\\]' | .)*? ']';

WS          : [ \t\r\n]+ -> skip ; // skip spaces, tabs, newlines, \r (Windows)

fragment INT    : '0' | [1-9] DIGIT* ;
fragment EXP    : [Ee] [+\-]? INT ;
fragment LETTER : [a-zA-Z\u0080-\u00FF_] ;
fragment DIGIT  : [0-9] ;
fragment ISODATE: ISOYEAR '-' ISOMONTH '-' ISODAY
                | ISOYEAR '-' ISOMONTH
                ;
fragment ISODATESHORT:  ISOYEAR ISOMONTH ISODAY;
fragment ISOTIME: ISOHOUR ':' ISOMINUTE (':' ISOSECOND ('.' ISOMILLI)?)?;
fragment ISOTIMESHORT: ISOHOUR ISOMINUTE (ISOSECOND ('.' ISOMILLI)?)? ;
fragment ISOYEAR: DIGIT DIGIT DIGIT DIGIT ;
fragment ISOMONTH: '0' [1-9] | '10' | '11' | '12' ;
fragment ISODAY: '0' [1-9] | [1-2] DIGIT | '30' | '31' ;
fragment ISOHOUR: [0-1] DIGIT | '2' [0-3] ;
fragment ISOMINUTE: [0-5] DIGIT ;
fragment ISOSECOND: ISOMINUTE ;
fragment ISOMILLI: DIGIT DIGIT DIGIT ;
fragment ISOTIMEZONE: 'Z' | ('+'|'-') ISOTZONEHOUR (':'? ISOMINUTE)? ;
fragment ISOTZONEHOUR: '0' DIGIT | '10' | '11' | '12' ;
