grammar Javamm;

@header {
    package pt.up.fe.comp2024.grammar;
}

// Operators
DIV: '/';
MUL: '*';
ADD: '+';
SUB: '-';
NOT: '!';
LOGICAL: '&&';
EQUALS: '=';
RELACIONAL: '<';

// Pontuation
SEMI: ';';
COL: ',';
DOT: '.';
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
WHILE: 'while';
IF: 'if';
ELSE: 'else';
NEW: 'new';

INTEGER: '0' | [1-9][0-9]*;
ID: [_$a-zA-Z]+ [_$a-zA-Z0-9]*;
WS: [ \t\n\r\f]+ -> skip;
COMMENT: '/*' .*? '*/' -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;

program: (importDecl)* classDecl EOF;

importDecl: IMPORT name += ID (DOT name += ID)* SEMI # Import;

classDecl:
	CLASS name = ID (EXTENDS superClass = ID)? LCURLY varDecl* methodDecl* RCURLY;

varDecl: type name = ID SEMI;

type
	locals[boolean isArray=false]:
	name = ID (LBRACKETS RBRACKETS {$isArray=true;})?;

typeOrVargs
	locals[boolean isArray=false,boolean isVarArgs=false]:
	name = ID (LBRACKETS RBRACKETS {$isArray=true;})?
	| name = ID ('...' {$isArray=true;$isVarArgs=true;});

methodDecl
	locals[boolean isPublic=false]:
	(PUBLIC {$isPublic=true;})? 'static'? type name = ID LPAREN (
		param (COL param)*
	)? RPAREN LCURLY varDecl* stmt* RCURLY;

param: typeOrVargs name = ID;

expr:
	op = NOT expr													# BinaryExpr
	| LPAREN expr RPAREN											# ParenExpr
	| expr DOT field = ID											# FieldAccessExpr
	| expr DOT functionName = ID LPAREN (expr (COL expr)*)? RPAREN	# FuncExpr
	| functionName = ID (LPAREN (expr (COL expr)*)? RPAREN)			# SelfFuncExpr
	| expr op = (DIV | MUL) expr									# BinaryExpr
	| expr op = (SUB | ADD) expr									# BinaryExpr
	| expr op = (LOGICAL | RELACIONAL) expr							# BinaryExpr
	| expr LBRACKETS expr RBRACKETS									# ArrayDeclExpr
	| NEW ID LBRACKETS expr RBRACKETS								# NewArrayExpr
	| NEW name = ID LPAREN RPAREN									# NewExpr
	| LBRACKETS (expr (COL expr)*)? RBRACKETS						# ArrayExpr
	| value = INTEGER												# IntegerLiteral
	| name = ID														# VarRefExpr;

stmt:
	LCURLY (stmt)* RCURLY								# MultiStmt
	| IF LPAREN expr RPAREN stmt ELSE stmt				# IfStmt
	| WHILE LPAREN expr RPAREN stmt						# WhileStmt
	| expr SEMI											# VarStmt
	| name = ID EQUALS expr SEMI						# AssignStmt
	| expr LBRACKETS expr RBRACKETS EQUALS expr SEMI	# AssignStmtArray
	| RETURN expr SEMI									# ReturnStmt;