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
ID: [a-zA-Z]+ [0-9]*;
WS: [ \t\n\r\f]+ -> skip;

program: (importDecl)* classDecl EOF;

importDecl: IMPORT name = ID (POINT ID)* SEMI # Import;

classDecl:
	CLASS name = ID (EXTENDS superr=ID)? LCURLY varDecl* methodDecl* RCURLY;

varDecl: type name = ID SEMI;

type
    locals[boolean isArray=false]:
	name = ID (LBRACKETS RBRACKETS {$isArray=true;})?
	| name = ID '...';

methodDecl
	locals[boolean isPublic=false]:
	(PUBLIC {$isPublic=true;})? 'static'? type name = ID LPAREN (
		param (COL param)*
	)? RPAREN LCURLY varDecl* stmt* RCURLY;

param: type name = ID;

expr:
	op = NEG expr										# BinaryExpr
	| expr op = (DIV | MUL) expr						# BinaryExpr
	| expr op = (SUB | ADD) expr						# BinaryExpr
	| expr op = (LOGICAL | RELACIONAL) expr				# BinaryExpr
	| expr LBRACKETS expr RBRACKETS						# ArrayDeclExpr
	| expr POINT LENGTH									# FuncExpr
	| expr POINT ID LPAREN (expr (COL expr)*)? RPAREN	# FuncExpr
	| NEW ID LBRACKETS expr RBRACKETS					# AssignExpr
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