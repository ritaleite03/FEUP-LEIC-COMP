package pt.up.fe.comp2024.analysis.passes;

import com.sun.tools.jconsole.JConsoleContext;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisPass;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class Test implements AnalysisPass {
    private String currentMethod;
    private final List<Report> reports = new ArrayList<>();

    private void addNewReport(String message, JmmNode expr){
        reports.add(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(expr),
                NodeUtils.getColumn(expr),
                message,
                null));
    }

    protected Type visitUnaryExpression(JmmNode expr,SymbolTable table){
        if(visitExpression(expr.getChild(0),table).getName().equals("boolean")){
            return new Type("boolean",false);
        }
        addNewReport("Unary Expression : argument not boolean",expr);
        return null;
    }

    protected Type visitBinaryExpression(JmmNode expr, SymbolTable table, boolean is_boolean) {

        var left = visitExpression(expr.getChild(0), table);
        var right = visitExpression(expr.getChild(1), table);

        switch (expr.get("op")){
            case "+", "-", "*", "/":
                // if it is INT and the expression does not need to be boolean (for loops and ifs conditions)
                if(left.getName().equals("int") && !left.isArray() && left.equals(right) && !is_boolean){
                    return new Type("int",false);
                }
            case "<", "==":
                if(left.getName().equals("int") && !left.isArray()  && left == right){
                    return new Type("boolean",false);
                }
            case  "&&":
                if(left.getName().equals("boolean") && !left.isArray()  && left == right){
                    return new Type("boolean",false);
                }
        }
        addNewReport("Arithmetic Expression : at least one argument not of right type or it is not expected an arithmetic expression",expr);
        return null;
    }

    protected Type visitAssignStatement(JmmNode expr, SymbolTable table){

        var left = visitVariableReferenceExpression(expr.get("name"), table,expr);
        var right = visitExpression(expr.getChild(0), table);

        if(!left.isArray() && !left.equals(right)){

            // deal with imports
            if(table.getImports().contains(left.getName()) && table.getImports().contains(right.getName())) return null;
            // deal with extends
            if((table.getClassName().equals(left.getName()) && table.getSuper().contains(right.getName())) || table.getClassName().equals(right.getName()) && table.getSuper().contains(left.getName())) return null;

            addNewReport("Assign Statement : Assignment of not array variable wrong",expr);
        }
        else if(left.isArray() && right==null){
            expr.getChild(0).getChildren().forEach((child)->{
                if(!visitExpression(child,table).getName().equals(left.getName())){
                    addNewReport("Assign Statement : Assignment of array variable wrong",expr);
                }
            });
        }
        return null;
    }

    protected Type visitArrayDeclarationExpression(JmmNode expr, SymbolTable table){

        var left = visitExpression(expr.getChild(0), table);
        var right = visitExpression(expr.getChild(1), table);

        if(!left.isArray()){
            addNewReport("Array Declaration Expression : left node not an array",expr);
            return null;
        }
        else if (!right.getName().equals("int")){
            addNewReport("Array Declaration Expression : right node not an int",expr);
            return null;
        }
        return null;
    }
    protected Type visitFunctionExpression(JmmNode expr, SymbolTable table){
        var child = visitExpression(expr.getChild(0), table);
        System.out.println("entrou");
        System.out.println(expr.getChildren());
        // method declared in class
        if (table.getClassName().equals(child.getName()) && table.getMethods().contains(expr.getObject("functionName").toString())) {
            Optional<Type> returnTypeOptional = table.getReturnTypeTry(expr.get("functionName"));
            return returnTypeOptional.orElse(null);
        }
        // supposed method declared in super
        if(table.getSuper()!=null) return null;
        // supposed method is from import
        if(table.getImports().contains(child.getName())) return null;

        addNewReport("Function Expression : right side not declared",expr);

        return null;
    }
    protected Type visitVariableReferenceExpression(String name,SymbolTable table,JmmNode expr){
        if(name.equals("true") || name.equals("false")){
            return new Type("boolean",false);
        }

        List<Symbol> symbols = new ArrayList<>();
        symbols.addAll(table.getLocalVariables(currentMethod));
        symbols.addAll(table.getParameters(currentMethod));
        symbols.addAll(table.getFields());

        var symbol = symbols.stream()
                .filter(param -> param.getName().equals(name)).findFirst();

        if(symbol.isPresent()){
            return symbol.get().getType();
        }
        addNewReport("Variable Reference Expression : variable not declared",expr);

        return null;
    }
    protected Type visitExpression(JmmNode expr, SymbolTable table){
        return switch (expr.getKind()) {
            case "IntegerLiteral" -> new Type("int", false);
            case "VarRefExpr" -> visitVariableReferenceExpression(expr.get("name"), table,expr);
            case "NewExpr" -> new Type(expr.get("name"), false);
            case "NewArrayExpr" -> new Type("int", true);
            case "UnaryExpr" -> visitUnaryExpression(expr,table);
            case "BinaryExpr" -> visitBinaryExpression(expr, table, false);
            case "ArrayDeclExpr" -> visitArrayDeclarationExpression(expr, table);
            case "IfStmt", "WhileStmt" -> visitBinaryExpression(expr.getChild(0), table, true);
            case "FuncExpr" -> visitFunctionExpression(expr, table);
            case "AssignStmt" -> visitAssignStatement(expr, table);
            default -> null;
        };
    }
    protected void visitMethods(JmmNode method, SymbolTable table){
        currentMethod = method.get("name");
        method.getChildren("VarStmt").forEach(stmt-> visitExpression(stmt.getChild(0),table));
        method.getChildren("AssignStmt").forEach(child-> visitExpression(child, table));
        method.getChildren("IfStmt").forEach(stmt-> visitExpression(stmt,table));
        method.getChildren("WhileStmt").forEach(stmt-> visitExpression(stmt,table));
        method.getChildren("ReturnStmt").forEach(stmt-> {
            System.out.println("oi");
            System.out.println(stmt.getChild(0));
            Type typeExpr = visitExpression(stmt.getChild(0), table);
            Optional<Type> typeReturn = table.getReturnTypeTry(method.get("name"));
            if(typeReturn.isPresent()) {
                if (typeExpr != null && !typeExpr.equals(typeReturn.get()))
                    addNewReport("Return Statement : wrong type", stmt.getChild(0));
            }
        });
    }
    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        root.getChildren(Kind.CLASS_DECL).get(0).getChildren(Kind.METHOD_DECL).stream().forEach(method->
                visitMethods(method,table));
        return reports;
    }
}
