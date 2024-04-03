package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisPass;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Test implements AnalysisPass {
    private String currentMethod;
    private final List<Report> reports = new ArrayList<>();

    private void addNewReport(String message, JmmNode expr) {
        reports.add(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(expr),
                NodeUtils.getColumn(expr),
                message,
                null));
    }

    protected Type visitUnaryExpression(JmmNode expr, SymbolTable table) {
        if (visitExpression(expr.getChild(0), table).getName().equals("boolean")) {
            return new Type("boolean", false);
        }
        addNewReport("Unary Expression : argument not boolean", expr);
        return null;
    }

    protected Type visitBinaryExpression(JmmNode expr, SymbolTable table, boolean is_boolean) {

        var left = visitExpression(expr.getChild(0), table);
        var right = visitExpression(expr.getChild(1), table);

        System.out.print("arithmetic expression ");
        System.out.println(expr);
        System.out.println(left);
        System.out.println(right);
        System.out.println(is_boolean);
        switch (expr.get("op")) {
            case "+", "-", "*", "/":
                // if it is INT and the expression does not need to be boolean (for loops and
                // ifs conditions)
                if (left.getName().equals("int") && !left.isArray() && left.equals(right) && !is_boolean) {
                    return new Type("int", false);
                }
            case "<", "==":
                if (left.getName().equals("int") && !left.isArray() && left == right) {
                    return new Type("boolean", false);
                }
            case "&&":
                if (left.getName().equals("boolean") && !left.isArray() && left == right) {
                    return new Type("boolean", false);
                }
        }
        addNewReport(
                "Arithmetic Expression : at least one argument not of right type or it is not expected an arithmetic expression",
                expr);
        return null;
    }

    protected void visitAssignStatement(JmmNode expr, SymbolTable table) {

        var left = visitVariableReferenceExpression(expr.get("name"), table, expr);
        var right = visitExpression(expr.getChild(0), table);
        if (right == null)
            return;

        if (left.equals(right)) {
            return;
        }

        if (left.isArray()) {
            addNewReport("Assign Statement : Assignment of array variable wrong", expr);
        }

        boolean leftIsImport = table.getImports().contains(left.getName());
        boolean leftIsMain = table.getClassName().equals(left.getName());
        boolean leftIsSuper = table.getSuper() != null && table.getSuper().equals(left.getName());
        boolean rightIsImport = table.getImports().contains(right.getName());
        boolean rightIsMain = table.getClassName().equals(right.getName());
        boolean rightIsSuper = table.getSuper() != null && table.getSuper().equals(right.getName());
        // deal with imports
        if (leftIsImport && !rightIsMain) {
            return;
        }
        if (leftIsImport && rightIsImport)
            return;
        // deal with extends
        if (rightIsMain && leftIsSuper)
            return;
        if (leftIsMain && rightIsSuper)
            return;

        addNewReport("Assign Statement : Assignment of not array variable wrong", expr);
    }

    protected Type visitArrayDeclarationExpression(JmmNode expr, SymbolTable table) {

        var left = visitExpression(expr.getChild(0), table);
        var right = visitExpression(expr.getChild(1), table);

        if (!left.isArray()) {
            addNewReport("Array Declaration Expression : left node not an array", expr);
            return null;
        } else if (!right.getName().equals("int")) {
            addNewReport("Array Declaration Expression : right node not an int", expr);
            return null;
        }
        return null;
    }

    protected Type visitFunctionExpression(JmmNode expr, SymbolTable table) {
        String functionName = expr.get("functionName");
        Type child = null;
        boolean isMainClass = true;
        var arguments = expr.getChildren();

        if (expr.getKind().equals("FuncExpr")) {
            isMainClass = false;
            child = visitExpression(expr.getChild(0), table);
            arguments = arguments.stream().skip(1).toList();
        }
        System.out.print("child: ");
        System.out.print(isMainClass);
        System.out.print(" ");
        System.out.println(child);
        isMainClass = isMainClass || (child != null && table.getClassName().equals(child.getName()));
        // class object and function declared
        if (!isMainClass)
            return null;
        if (table.getMethods().contains(functionName)) {
            // different number of parameters
            List<Symbol> parametersMethod = table.getParameters(functionName);
            boolean isVarArgs = parametersMethod.size() > 0
                    && TypeUtils.isVarArgs(parametersMethod.get(parametersMethod.size() - 1).getType());
            if (!isVarArgs && arguments.size() != parametersMethod.size()) {
                addNewReport("Function Expression : wrong number of parameters", expr);
                return null;
            }
            for (int i = 0; i < parametersMethod.size(); i++) {
                Type argument = visitExpression(arguments.get(i), table);
                Type parameter = parametersMethod.get(i).getType();
                if (!argument.equals(parameter)
                        && !(TypeUtils.isVarArgs(parameter) && parameter.getName().equals(argument.getName()))) {
                    addNewReport("Function Expression : parameter with wrong type", arguments.get(i));
                    return null;
                }
            }
            for (int i = parametersMethod.size(); i < arguments.size(); i++) {
                Type argument = visitExpression(arguments.get(i), table);
                Type parameter = parametersMethod.get(parametersMethod.size() - 1).getType();
                if (!parameter.getName().equals(argument.getName())) {
                    addNewReport("Function Expression : parameter with wrong type2", arguments.get(i));
                    return null;
                }
            }

            Optional<Type> returnTypeOptional = table.getReturnTypeTry(expr.get("functionName"));
            return returnTypeOptional.orElse(null);
        }
        // supposed method declared in super
        if (table.getSuper() != null)
            return null;

        addNewReport("Function Expression : right side not declared", expr);

        return null;
    }

    protected Type visitVariableReferenceExpression(String name, SymbolTable table, JmmNode expr) {
        if (name.equals("true") || name.equals("false")) {
            return new Type("boolean", false);
        }

        if (name.equals("this")) {
            return new Type(table.getClassName(), false);
        }

        List<Symbol> symbols = new ArrayList<>();
        symbols.addAll(table.getLocalVariables(currentMethod));
        symbols.addAll(table.getParameters(currentMethod));
        symbols.addAll(table.getFields());

        var symbol = symbols.stream()
                .filter(param -> param.getName().equals(name)).findFirst();

        if (symbol.isPresent()) {
            return symbol.get().getType();
        }
        if (table.getImports().contains(name)) {
            return null;
        }
        addNewReport("Variable Reference Expression : variable not declared", expr);

        return null;
    }

    protected Type visitFieldAccessExpression(JmmNode expr, SymbolTable table) {
        Type rigth = visitExpression(expr.getChild(0), table);
        System.out.println("expr");
        if (rigth == null) {
            return null;
        }
        String field = expr.get("field");
        if (rigth.isArray() && field.equals("legth")) {
            return new Type("int", false);
        }
        if (rigth.isArray() || !rigth.getName().equals(table.getClassName())) {
            addNewReport(currentMethod, expr);
            return null;
        }

        var symbol = table.getFields().stream()
                .filter(param -> param.getName().equals(field)).findFirst();

        if (symbol.isPresent()) {
            return symbol.get().getType();
        }
        addNewReport("Field Access Expression : field not declared", expr);

        return null;
    }

    protected Type visitArrayExpr(JmmNode expr, SymbolTable table) {
        for (var child : expr.getChildren()) {
            Type type = visitExpression(child, table);
            if (type.isArray() || !type.getName().equals("int")) {
                addNewReport("Array Initializer Expression : invalid array initializer", child);
                return null;
            }
        }
        return new Type("int", true);
    }

    protected Type visitExpression(JmmNode expr, SymbolTable table) {
        System.out.println(expr.getKind());
        var a = switch (expr.getKind()) {
            case "IntegerLiteral" -> new Type("int", false);
            case "VarRefExpr" -> visitVariableReferenceExpression(expr.get("name"), table, expr);
            case "FieldAccessExpr" -> visitFieldAccessExpression(expr, table);
            case "NewExpr" -> new Type(expr.get("name"), false);
            case "ArrayExpr" -> visitArrayExpr(expr, table);
            case "NewArrayExpr" -> new Type("int", true);
            case "UnaryExpr" -> visitUnaryExpression(expr, table);
            case "BinaryExpr" -> visitBinaryExpression(expr, table, false);
            case "ArrayDeclExpr" -> visitArrayDeclarationExpression(expr, table);
            case "IfStmt", "WhileStmt" -> visitBinaryExpression(expr.getChild(0), table, true);
            case "FuncExpr", "SelfFuncExpr" -> visitFunctionExpression(expr, table);
            default -> null;
        };
        return a;
    }

    protected void visitMethods(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        var params = table.getParameters(currentMethod);
        for (int i = 0; i < params.size() - 1; i++) {
            if (TypeUtils.isVarArgs(params.get(i).getType())) {
                addNewReport("VarArgs : VarArgs are only valid as the last argument",
                        method);
            }
        }
        method.getChildren("VarStmt").forEach(stmt -> visitExpression(stmt.getChild(0), table));
        method.getChildren("AssignStmt").forEach(stmt -> visitAssignStatement(stmt, table));
        method.getChildren("IfStmt").forEach(stmt -> visitExpression(stmt, table));
        method.getChildren("WhileStmt").forEach(stmt -> visitExpression(stmt, table));
        method.getChildren("ReturnStmt").forEach(stmt -> {
            System.out.println("oi");
            System.out.println(stmt.getChild(0));
            Type typeExpr = visitExpression(stmt.getChild(0), table);
            Optional<Type> typeReturn = table.getReturnTypeTry(method.get("name"));
            if (typeReturn.isPresent()) {
                if (typeExpr != null && !typeExpr.equals(typeReturn.get()))
                    addNewReport("Return Statement : wrong type", stmt.getChild(0));
            }
        });
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        root.getChildren(Kind.CLASS_DECL).get(0).getChildren(Kind.METHOD_DECL).stream()
                .forEach(method -> visitMethods(method, table));
        return reports;
    }
}
