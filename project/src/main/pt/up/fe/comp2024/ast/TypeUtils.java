package pt.up.fe.comp2024.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    public static List<Report> reports;
    public static String currentMethod;

    protected static void addNewReport(String message, JmmNode expr) {
        if (reports == null)
            return;
        reports.add(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(expr),
                NodeUtils.getColumn(expr),
                message,
                null));
    }

    protected static Type visitUnaryExpression(JmmNode expr, SymbolTable table) {
        if (getExprType(expr.getChild(0), table).getName().equals("boolean")) {
            return new Type("boolean", false);
        }
        addNewReport("Unary Expression : argument not boolean", expr);
        return null;
    }

    protected static Type visitBinaryExpression(JmmNode expr, SymbolTable table) {

        var left = getExprType(expr.getChild(0), table);
        var right = getExprType(expr.getChild(1), table);

        if (left == null || right == null) {
            return null;
        }

        System.out.print("arithmetic expression ");
        System.out.println(expr);
        System.out.println(left);
        System.out.println(right);
        switch (expr.get("op")) {
            case "+", "-", "*", "/":
                // if it is INT and the expression does not need to be boolean (for loops and
                // ifs conditions)
                if (left.getName().equals("int") && !left.isArray() && left.equals(right)) {
                    return new Type("int", false);
                }
            case "<", "==":
                if (left.getName().equals("int") && !left.isArray() && left.equals(right)) {
                    return new Type("boolean", false);
                }
            case "&&":
                if (left.getName().equals("boolean") && !left.isArray() && left.equals(right)) {
                    return new Type("boolean", false);
                }
        }
        addNewReport(
                "Arithmetic Expression : at least one argument not of right type or it is not expected an arithmetic expression",
                expr);
        return null;
    }

    protected static Type visitArrayDeclarationExpression(JmmNode expr, SymbolTable table) {

        var left = getExprType(expr.getChild(0), table);
        var right = getExprType(expr.getChild(1), table);

        if (!left.isArray()) {
            addNewReport("Array Declaration Expression : left node not an array", expr);
            return null;
        } else if (!right.getName().equals("int")) {
            addNewReport("Array Declaration Expression : right node not an int", expr);
            return null;
        }
        return null;
    }

    protected static Type visitFunctionExpression(JmmNode expr, SymbolTable table) {
        String functionName = expr.get("functionName");
        Type child = null;
        boolean isMainClass = true;
        var arguments = expr.getChildren();

        if (expr.getKind().equals("FuncExpr")) {
            isMainClass = false;
            child = getExprType(expr.getChild(0), table);
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
                Type argument = getExprType(arguments.get(i), table);
                Type parameter = parametersMethod.get(i).getType();
                if (!argument.equals(parameter)
                        && !(TypeUtils.isVarArgs(parameter) && parameter.getName().equals(argument.getName()))) {
                    addNewReport("Function Expression : parameter with wrong type", arguments.get(i));
                    return null;
                }
            }
            for (int i = parametersMethod.size(); i < arguments.size(); i++) {
                Type argument = getExprType(arguments.get(i), table);
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

    public static Type visitVariableReferenceExpression(String name, SymbolTable table, JmmNode expr) {
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
        if (table.getSuper() != null) {
            return null;
        }
        addNewReport("Variable Reference Expression : variable not declared", expr);

        return null;
    }

    protected static Type visitFieldAccessExpression(JmmNode expr, SymbolTable table) {
        Type rigth = getExprType(expr.getChild(0), table);
        System.out.println("expr");
        if (rigth == null) {
            return null;
        }
        String field = expr.get("field");
        if (rigth.isArray() && field.equals("length")) {
            return new Type("int", false);
        }
        if (rigth.isArray()) {
            addNewReport("Not .length on array", expr);
            return null;
        }
        if (table.getImports().contains(rigth.getName())) {
            return null;
        }

        var symbol = table.getFields().stream()
                .filter(param -> param.getName().equals(field)).findFirst();

        if (symbol.isPresent()) {
            return symbol.get().getType();
        }

        if (table.getSuper() == null)
            addNewReport("Field Access Expression : field not declared", expr);

        return null;
    }

    protected static Type visitArrayExpr(JmmNode expr, SymbolTable table) {
        for (var child : expr.getChildren()) {
            Type type = getExprType(child, table);
            if (type.isArray() || !type.getName().equals("int")) {
                addNewReport("Array Initializer Expression : invalid array initializer", child);
                return null;
            }
        }
        return new Type("int", true);
    }

    public static Type getExprType(JmmNode expr, SymbolTable table) {
        System.out.println(expr.getKind());
        var a = switch (expr.getKind()) {
            case "IntegerLiteral" -> new Type("int", false);
            case "VarRefExpr" -> visitVariableReferenceExpression(expr.get("name"), table, expr);
            case "FieldAccessExpr" -> visitFieldAccessExpression(expr, table);
            case "NewExpr" -> new Type(expr.get("name"), false);
            case "ArrayExpr" -> visitArrayExpr(expr, table);
            case "NewArrayExpr" -> new Type("int", true);
            case "UnaryExpr" -> visitUnaryExpression(expr, table);
            case "BinaryExpr" -> visitBinaryExpression(expr, table);
            case "ArrayDeclExpr" -> visitArrayDeclarationExpression(expr, table);
            case "FuncExpr", "SelfFuncExpr" -> visitFunctionExpression(expr, table);
            default -> null;
        };
        return a;
    }

    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // TODO: Simple implementation that needs to be expanded
        return sourceType.getName().equals(destinationType.getName());
    }

    public static boolean isVarArgs(Type type) {
        return type.getObject("isVarArg", Boolean.class);
    }

    public static void setVarArgs(Type type, Boolean value) {
        type.putObject("isVarArg", value);
    }

}
