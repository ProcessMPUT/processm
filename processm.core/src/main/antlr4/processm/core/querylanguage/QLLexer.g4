lexer grammar QLLexer;
// lexer rules start with uppercase letters
tokens {STRING}
@lexer::members {
    private void go(int diff) {
        _input.seek(_input.index() + diff);
    }
}


SELECT      : 'select' ;
WHERE       : 'where' ;
GROUP_BY    : 'group' [ \t\r\n]+ 'by' ;
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

STRING_SINGLE : SCOPE_PREFIX '\'' ( '\\\'' | . )*? '\'' -> type(STRING) ;
STRING_DOUBLE : SCOPE_PREFIX '"' ( '\\"' | . )*? '"' -> type(STRING) ;

NUMBER      : SCOPE_PREFIX '-'? INT ('.' [0-9] +)? EXP? ;
BOOLEAN     : SCOPE_PREFIX 'true' | 'false' ;
DATETIME    : SCOPE_PREFIX 'D' ISODATE ('T' ISOTIME ISOTIMEZONE?)?
            | SCOPE_PREFIX 'D' ISODATESHORT ('T'? ISOTIMESHORT ISOTIMEZONE?)?
            ;

NULL        : SCOPE_PREFIX 'null' ;

FUNC_AGGR   : SCOPE_PREFIX ('min' | 'max' | 'avg' | 'count' | 'sum') '(' { go(-1); } ;
FUNC_SCALAR1: SCOPE_PREFIX ('date' | 'time' | 'year' | 'month' | 'day' | 'hour' | 'minute' | 'second' | 'millisecond' |
                            'quarter' | 'dayofweek' | 'upper' | 'lower' | 'round') '(' { go(-1); } ;
FUNC_SCALAR0: SCOPE_PREFIX 'now' '(' { go(-1); } ;

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

ID          : SCOPE_PREFIX (LETTER|DIGIT)+ (':' (LETTER|DIGIT)+)?
            | '[' SCOPE_PREFIX .+? ']';

LINE_COMMENT: ( '//' | '--' ) .*? ( '\r' | '\n' ) -> skip ;
COMMENT     : '/*' .*? '*/' -> skip ;
WS          : [ \t\r\n]+ -> skip ; // skip spaces, tabs, newlines, \r (Windows)

fragment HOISTING_PREFIX: '^' ;
fragment SCOPE_PREFIX: 'log' ':'
                | 'l' ':'
                | HOISTING_PREFIX? 'trace' ':'
                | HOISTING_PREFIX? 't' ':'
                | HOISTING_PREFIX? HOISTING_PREFIX? 'event' ':'
                | HOISTING_PREFIX? HOISTING_PREFIX? 'e' ':'
                | HOISTING_PREFIX? HOISTING_PREFIX?
                ;
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
