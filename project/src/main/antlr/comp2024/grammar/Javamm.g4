grammar Javamm;

@header {
    package pt.up.fe.comp2024.grammar;
}

// Operators
DIV: '/';
MUL: '*';
ADD: '+';
SUB: '-';
NEG: '!';
LOGICAL: '&&';
EQUALS: '=';
RELACIONAL: '<';

// Pontuation
SEMI: ';';
COL: ',';
POINT: '.';
LCURLY: '{';
RCURLY: '}';
LPAREN: '(';
RPAREN: ')';
LBRACKETS: '[';
RBRACKETS: ']';

// Words
CLASS: 'class';
INT: 'int';
BOOLEAN: 'boolean';
STRING: 'String';
PUBLIC: 'public';
RETURN: 'return';
IMPORT: 'import';
EXTENDS: 'extends';
LENGTH: 'length';
WHILE: 'while';
IF: 'if';
ELSE: 'else';
NEW: 'new';

INTEGER: [0-9]+;
ALNUM: [a-zA-Z0-9_];
ID: [a-zA-Z_] ALNUM*;
CLASS_NAME: [A-Z]ALNUM*;
WS: [ \t\n\r\f]+ -> skip;

program: (importDecl)* classDecl EOF;

importDecl: IMPORT ID (POINT ID)* SEMI # Import;

classDecl:
	CLASS name = CLASS_NAME (EXTENDS CLASS_NAME)? LCURLY varDecl* methodDecl* RCURLY;

varDecl: type name = ID SEMI;

type:
	name = INT
	| INT LBRACKETS RBRACKETS
	| INT '...'
	| BOOLEAN
	| STRING
	| ID;

methodDecl
	locals[boolean isPublic=false]:
	(PUBLIC {$isPublic=true;})? type name = ID LPAREN (
		param (COL param)*
	)? RPAREN LCURLY varDecl* stmt* RCURLY
	| (PUBLIC {$isPublic=true;})? 'static' 'void' 'main' LPAREN STRING LBRACKETS RBRACKETS ID RPAREN
		LCURLY varDecl* stmt* RCURLY;

param: type name = ID;

expr:
	op = NEG expr										# UnaryExpr
	| expr op = (DIV | MUL) expr						# BinaryExpr
	| expr op = (SUB | ADD) expr						# BinaryExpr
	| expr op = (LOGICAL | RELACIONAL) expr				# BinaryExpr
	| expr LBRACKETS expr RBRACKETS						# ArrayDeclExpr
	| expr POINT LENGTH									# FuncExpr
	| expr POINT ID LPAREN (expr (COL expr)*)? RPAREN	# FuncExpr
	| NEW INT LBRACKETS expr RBRACKETS					# AssignExpr
	| NEW ID LPAREN RPAREN								# FuncExpr
	| LPAREN expr RPAREN								# Array
	| LBRACKETS (expr (COL expr)*)? RBRACKETS			# Array
	| value = INTEGER									# IntegerLiteral
	| name = ID											# VarRefExpr;

stmt:
	LCURLY (stmt)* RCURLY								# MultiStmt
	| IF LPAREN expr RPAREN stmt ELSE stmt				# IfStmt
	| WHILE LPAREN expr RPAREN stmt						# WhileStmt
	| expr SEMI											# VarStmt
	| expr EQUALS expr SEMI								# AssignStmt
	| expr LBRACKETS expr RBRACKETS EQUALS expr SEMI	# AssignStmt
	| RETURN expr SEMI									# ReturnStmt;