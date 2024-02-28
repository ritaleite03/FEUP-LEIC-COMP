grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

// Operators
DIV : '/' ;
MUL : '*' ;
ADD : '+' ;
SUB : '-' ;
NEG : '!' ;
LOGICAL : '&&';
EQUALS : '=';
RELACIONAL  : '<';

// Pontuation
SEMI : ';' ;
COL : ',' ;
POINT : '.';
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LBRACKETS : '[' ;
RBRACKETS : ']' ;

// Words
CLASS : 'class' ;
INT : 'int' ;
BOOLEAN : 'boolean';
STRING : 'String';
PUBLIC : 'public' ;
RETURN : 'return' ;
IMPORT : 'import';
EXTENDS : 'extends';
LENGTH : 'length';
WHILE : 'while';
IF : 'if';
ELSE : 'else';
NEW : 'new';

INTEGER : [0-9]+ ;
ID : [a-zA-Z]+[0-9]*;
WS : [ \t\n\r\f]+ -> skip ;

program
    : classDecl EOF
    ;

importDecl
    : IMPORT ID #Import;

classDecl
    : CLASS name= ID (EXTENDS ID)? LCURLY varDecl* methodDecl* RCURLY
    ;

varDecl
    : type name= ID SEMI
    ;

type
    : name= INT
    | INT LBRACKETS RBRACKETS
    | INT '...'
    | BOOLEAN
    | STRING
    | ID;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN (param (COL param)*)? RPAREN
        LCURLY varDecl* stmt* RCURLY;

mainDecl
    : (PUBLIC)?
        'static' 'void' 'main'
        LPAREN STRING LBRACKETS RBRACKETS ID RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

param
    : type name=ID
    ;

stmt
    : LCURLY (stmt)* RCURLY                             #MultiStmt
    | IF LPAREN expr RPAREN stmt ELSE stmt              #IfStmt
    | WHILE LPAREN expr RPAREN stmt                     #WhileStmt
    | expr SEMI                                         #VarStmt
    | expr EQUALS expr SEMI                             #AssignStmt
    | RETURN expr SEMI                                  #ReturnStmt
    ;

expr
    : op= NEG expr                                      #BinaryExpr
    | expr op= (DIV | MUL) expr                         #BinaryExpr
    | expr op= (SUB | ADD) expr                         #BinaryExpr
    | expr op= (LOGICAL | RELACIONAL) expr              #BinaryExpr
    | expr LBRACKETS expr RBRACKETS                     #ArrayDeclExpr
    | expr POINT LENGTH                                 #FuncExpr
    | expr POINT ID LPAREN (expr (COL expr)* )? RPAREN  #FuncExpr
    | NEW INT LBRACKETS expr RBRACKETS                  #AssignExpr
    | NEW ID LPAREN RPAREN                              #FuncExpr
    | LPAREN expr RPAREN                                #Array
    | LBRACKETS (expr (COL expr)* )? RBRACKETS          #Array
    | value= INTEGER                                    #IntegerLiteral
    | name= ID                                          #VarRefExpr
    ;



