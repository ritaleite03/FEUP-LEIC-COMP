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
<<<<<<< HEAD
LENGTH: 'length';
=======
>>>>>>> b0936470b7f306f602b3b44ba9493b74587e430a
WHILE: 'while';
IF: 'if';
ELSE: 'else';
NEW: 'new';

<<<<<<< HEAD
INTEGER: [0-9]+;
ID: [a-zA-Z]+ [0-9]*;
WS: [ \t\n\r\f]+ -> skip;
=======
INTEGER: '0' | [1-9][0-9]*;
ID: [_$a-zA-Z]+ [_$a-zA-Z0-9]*;
WS: [ \t\n\r\f]+ -> skip;
COMMENT: '/*' .*? '*/' -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;
>>>>>>> b0936470b7f306f602b3b44ba9493b74587e430a

program: (importDecl)* classDecl EOF;

importDecl: IMPORT name = ID (POINT ID)* SEMI # Import;

classDecl:
<<<<<<< HEAD
	CLASS name = ID (EXTENDS superr=ID)? LCURLY varDecl* methodDecl* RCURLY;
=======
	CLASS name = ID (EXTENDS superr = ID)? LCURLY varDecl* methodDecl* RCURLY;
>>>>>>> b0936470b7f306f602b3b44ba9493b74587e430a

varDecl: type name = ID SEMI;

type
<<<<<<< HEAD
    locals[boolean isArray=false]:
=======
	locals[boolean isArray=false]:
>>>>>>> b0936470b7f306f602b3b44ba9493b74587e430a
	name = ID (LBRACKETS RBRACKETS {$isArray=true;})?
	| name = ID '...';

methodDecl
	locals[boolean isPublic=false]:
	(PUBLIC {$isPublic=true;})? 'static'? type name = ID LPAREN (
		param (COL param)*
	)? RPAREN LCURLY varDecl* stmt* RCURLY;

param: type name = ID;

expr:
<<<<<<< HEAD
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
=======
	op = NEG expr											# BinaryExpr
	| expr op = (DIV | MUL) expr							# BinaryExpr
	| expr op = (SUB | ADD) expr							# BinaryExpr
	| expr op = (LOGICAL | RELACIONAL) expr					# BinaryExpr
	| expr LBRACKETS expr RBRACKETS							# ArrayDeclExpr
	| expr POINT ID (LPAREN (expr (COL expr)*)? RPAREN)?	# FuncExpr
	| NEW ID LBRACKETS expr RBRACKETS						# NewArrayExpr
	| NEW ID LPAREN RPAREN									# NewExpr
	| LPAREN expr RPAREN									# ParenExpr
	| LBRACKETS (expr (COL expr)*)? RBRACKETS				# ArrayExpr
	| value = INTEGER										# IntegerLiteral
	| name = ID												# VarRefExpr;
>>>>>>> b0936470b7f306f602b3b44ba9493b74587e430a

stmt:
	LCURLY (stmt)* RCURLY								# MultiStmt
	| IF LPAREN expr RPAREN stmt ELSE stmt				# IfStmt
	| WHILE LPAREN expr RPAREN stmt						# WhileStmt
	| expr SEMI											# VarStmt
	| expr EQUALS expr SEMI								# AssignStmt
	| expr LBRACKETS expr RBRACKETS EQUALS expr SEMI	# AssignStmt
	| RETURN expr SEMI									# ReturnStmt;