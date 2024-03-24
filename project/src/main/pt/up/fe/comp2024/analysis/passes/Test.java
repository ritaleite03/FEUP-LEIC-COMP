package pt.up.fe.comp2024.analysis.passes;

import com.sun.tools.jconsole.JConsoleContext;
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


public class Test implements AnalysisPass {
    private String currentMethod;
    private List<Report> reports = new ArrayList<>();

    protected Type visitBinaryExpression(JmmNode expr, SymbolTable table, boolean is_boolean) {
        var left = visitExpression(expr.getChild(0), table);
        var right = visitExpression(expr.getChild(1), table);

        switch (expr.get("op")){
            case "+", "-", "*", "/":
                if(left.getName().equals("int") && !left.isArray() && left.equals(right) && !is_boolean){
                    return new Type("int",false);
                }
                else{
                    var message = "Arithmetic Expression : at least one argument not int or it is not expected an arithmetic expression";
                    reports.add(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(expr),
                            NodeUtils.getColumn(expr),
                            message,
                            null));
                }
            case "<":
            case "==":
                if(left.getName().equals("int") && !left.isArray()  && left == right){
                    return new Type("boolean",false);
                }
            case  "&&":
                if(left.getName().equals("boolean") && !left.isArray()  && left == right){
                    return new Type("boolean",false);
                }
        }
        return null;
    }

    protected Type visitAssignStmt(JmmNode expr, SymbolTable table){
        var left = visitExpression(expr.getChild(0), table);
        var right = visitExpression(expr.getChild(1), table);

        if(!left.isArray() && !left.equals(right)){

            // deal with imports
            if(table.getImports().contains(left.getName()) && table.getImports().contains(right.getName())) return null;
            // deal with extends
            if((table.getClassName().equals(left.getName()) && table.getSuper().contains(right.getName())) || table.getClassName().equals(right.getName()) && table.getSuper().contains(left.getName())) return null;

            var message = "AssignStmt : Assignment of not array variable wrong";
            reports.add(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(expr),
                    NodeUtils.getColumn(expr),
                    message,
                    null));
        }
        else if(left.isArray() && right==null){
            expr.getChild(1).getChildren().forEach((child)->{
                if(!visitExpression(child,table).getName().equals(left.getName())){
                    var message = "AssignStmt : Assignment of array variable wrong";
                    reports.add(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(expr),
                            NodeUtils.getColumn(expr),
                            message,
                            null));

                }
                    }
                    );
        }
        return null;
    }

    protected Type visitArrayDeclExpr(JmmNode expr, SymbolTable table){
        var left = visitExpression(expr.getChild(0), table);
        var right = visitExpression(expr.getChild(1), table);

        if(!left.isArray()){
            var message = "ArrayDeclarationExpr : left node not an array";
            reports.add(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(expr),
                    NodeUtils.getColumn(expr),
                    message,
                    null));
            return null;
        }
        else if (!right.getName().equals("int")){
            var message = "ArrayDeclarationExpr : right node not an int";
            reports.add(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(expr),
                    NodeUtils.getColumn(expr),
                    message,
                    null));
            return null;
        }
        return null;
    }
    protected Type visitFuncExpr(JmmNode expr, SymbolTable table){
System.out.println("ola");
        var left = visitExpression(expr.getChild(0), table);
        var right = visitExpression(expr.getChild(1), table);

        if(table.getClassName().equals(left.getName()) && table.getMethods().contains((right.getName()))) return null;

        var message = "FuncExpr : right side not declared";
            reports.add(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(expr),
                    NodeUtils.getColumn(expr),
                    message,
                    null));
        return null;
    }
    protected Type visitExpression(JmmNode expr, SymbolTable table){
        switch (expr.getKind()){
            case "IntegerLiteral":
                return new Type("int",false);
            case "VarRefExpr":
                var name = expr.get("name");
                var symbol = table.getLocalVariables(currentMethod).stream()
                        .filter(param -> param.getName().equals(name)).findFirst();
                if(symbol.isPresent()) {
                    return symbol.get().getType();
                }
                symbol = table.getParameters(currentMethod).stream()
                        .filter(param -> param.getName().equals(name)).findFirst();
                if(symbol.isPresent()) {
                    return symbol.get().getType();
                }
                symbol = table.getFields().stream()
                        .filter(param -> param.getName().equals(name)).findFirst();
                if(symbol.isPresent()) {
                    return symbol.get().getType();
                }
                return null;
            case "NewExpr":
                return new Type(expr.get("name"),false);
            case "NewArrayExpr":
                return new Type("int",true);
            case "BinaryExpr":
                return  visitBinaryExpression(expr, table,false);
            case "AssignStmt":
                return visitAssignStmt(expr,table);
            case "ArrayDeclExpr":
                return visitArrayDeclExpr(expr,table);
            case "IfStmt":
            case "WhileStmt":
                visitBinaryExpression(expr.getChild(0),table,true);
            case "FuncExpr":
                visitFuncExpr(expr,table);

        }
        return null;
    }
    protected void visitMethods(JmmNode method, SymbolTable table){
        currentMethod = method.get("name");
        method.getChildren("AssignStmt").forEach(child-> visitExpression(child, table));
        method.getChildren("ReturnStmt").forEach(stmt-> stmt.getChildren().forEach(child-> visitExpression(child, table)));
        method.getChildren("IfStmt").forEach(stmt-> visitExpression(stmt,table));
        method.getChildren("WhileStmt").forEach(stmt-> visitExpression(stmt,table));
        method.getChildren("VarStmt").forEach(stmt-> visitExpression(stmt.getChild(0),table));
        System.out.println(method.getChildren("VarStmt"));
    }
    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        root.getChildren(Kind.CLASS_DECL).get(0).getChildren(Kind.METHOD_DECL).stream().forEach(method->
                visitMethods(method,table));
        return reports;
    }
}
