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
    public static boolean isStatic;

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
        var type = getExprType(expr.getChild(0), table);
        if (type == null || type.getName().equals("boolean")) {
            return new Type("boolean", false);
        }
        addNewReport("Unary Expression : argument not boolean", expr);
        return null;
    }

    protected static Type visitBinaryExpression(JmmNode expr, SymbolTable table) {

        var left = getExprType(expr.getChild(0), table);
        var right = getExprType(expr.getChild(1), table);

        switch (expr.get("op")) {
            case "+", "-", "*", "/":
                if (left == null || right == null) {
                    return new Type("int", false);
                }
                // if it is INT and the expression does not need to be boolean (for loops and
                // ifs conditions)
                if (left.getName().equals("int") && !left.isArray() && left.equals(right)) {
                    return new Type("int", false);
                }
                break;
            case "<", "==":
                if (left == null || right == null) {
                    return new Type("boolean", false);
                }
                if (left.getName().equals("int") && !left.isArray() && left.equals(right)) {
                    return new Type("boolean", false);
                }
                break;
            case "&&":
                if (left == null || right == null) {
                    return new Type("boolean", false);
                }
                if (left.getName().equals("boolean") && !left.isArray() && left.equals(right)) {
                    return new Type("boolean", false);
                }
                break;
        }
        addNewReport(
                "Arithmetic Expression : at least one argument not of right type or it is not expected an arithmetic expression",
                expr);
        return null;
    }

    protected static Type visitArrayAccessExpression(JmmNode expr, SymbolTable table) {

        var left = getExprType(expr.getChild(0), table);
        var right = getExprType(expr.getChild(1), table);
        if (left == null || right == null)
            return new Type("int", false);

        if (!left.isArray()) {
            addNewReport("Array Declaration Expression : left node not an array", expr);
            return null;
        } else if (!right.getName().equals("int") || right.isArray()) {
            addNewReport("Array Declaration Expression : right node not an int", expr);
            return null;
        }
        return new Type(left.getName(), false);
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
        } else {
            if (isStatic) {
                addNewReport("Self call in static method", expr);
                return null;
            }
        }
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
            boolean arrayInLast = false;
            if (parametersMethod.size() - arguments.size() > 1
                    || (parametersMethod.size() - arguments.size() == 1 && !TypeUtils.isVarArgs(parametersMethod
                            .get(parametersMethod.size() - 1).getType()))) {
                addNewReport("Function Expression : wrong number of parameters", expr);
                return null;
            }
            for (int i = 0; i < Math.min(arguments.size(), parametersMethod.size()); i++) {
                Type argument = getExprType(arguments.get(i), table);
                Type parameter = parametersMethod.get(i).getType();
                if (argument == null || parameter == null) {
                    continue;
                }
                if (!argument.equals(parameter)
                        && !(TypeUtils.isVarArgs(parameter) && parameter.getName().equals(argument.getName()))) {
                    addNewReport("Function Expression : parameter with wrong type", arguments.get(i));
                    return null;
                }
                if (i == parametersMethod.size() - 1 && argument.isArray()) {
                    arrayInLast = true;
                }
            }
            if (isVarArgs && arrayInLast && parametersMethod.size() != arguments.size()) {
                addNewReport("Varargs alredy handled by array", expr);
                return null;
            }
            for (int i = parametersMethod.size(); i < arguments.size(); i++) {
                Type argument = getExprType(arguments.get(i), table);
                Type parameter = parametersMethod.get(parametersMethod.size() - 1).getType();
                if (!parameter.getName().equals(argument.getName()) || argument.isArray()) {
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

        if (isStatic && name.equals("this")) {
            addNewReport("This in static method", expr);
            return null;
        }
        if (name.equals("this")) {
            return new Type(table.getClassName(), false);
        }

        List<Symbol> symbols = new ArrayList<>();
        symbols.addAll(table.getLocalVariables(currentMethod));
        symbols.addAll(table.getParameters(currentMethod));
        if (!isStatic) {
            symbols.addAll(table.getFields());
        }

        var symbol = symbols.stream()
                .filter(param -> param.getName().equals(name)).findFirst();

        if (symbol.isPresent()) {
            return symbol.get().getType();
        }
        if (isInImports(name, table)) {
            return null;
        }
        if (!isStatic && table.getSuper() != null) {
            return null;
        }
        addNewReport("Variable Reference Expression : variable not declared", expr);

        return null;
    }

    protected static Type visitFieldAccessExpression(JmmNode expr, SymbolTable table) {
        Type rigth = getExprType(expr.getChild(0), table);
        if (rigth == null) {
            addNewReport("Invalid field access of import", expr);
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
        if (!rigth.getName().equals(table.getClassName())) {
            addNewReport("Field Access Expression : field cannot be access because object is not the same as class",
                    expr);
            return null;
        }
        addNewReport("Invalid field access", expr);

        var symbol = table.getFields().stream()
                .filter(param -> param.getName().equals(field)).findFirst();

        if (symbol.isPresent()) {
            return symbol.get().getType();
        }

        if (table.getSuper() == null)
            addNewReport("Field Access Expression : field not declared", expr);

        return null;
    }

    protected static Type visitNewExpr(JmmNode expr, SymbolTable table) {
        var type = new Type(expr.get("name"), false);
        if (isValidType(type, table))
            return type;
        addNewReport("Invalid new expression", expr);
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

    protected static Type visitNewArrayExpr(JmmNode expr, SymbolTable table) {
        var type = getExprType(expr.getChild(0), table);
        if (type != null && (type.isArray() || !type.getName().equals("int"))) {
            addNewReport("Invalid size for new array", expr.getChild(0));
        }
        return new Type("int", true);
    }

    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var a = switch (expr.getKind()) {
            case "IntegerLiteral" -> new Type("int", false);
            case "ParenExpr" -> getExprType(expr.getChild(0), table);
            case "VarRefExpr" -> visitVariableReferenceExpression(expr.get("name"), table, expr);
            case "FieldAccessExpr" -> visitFieldAccessExpression(expr, table);
            case "NewExpr" -> visitNewExpr(expr, table);
            case "ArrayExpr" -> visitArrayExpr(expr, table);
            case "NewArrayExpr" -> visitNewArrayExpr(expr, table);
            case "UnaryExpr" -> visitUnaryExpression(expr, table);
            case "BinaryExpr" -> visitBinaryExpression(expr, table);
            case "ArrayAccessExpr" -> visitArrayAccessExpression(expr, table);
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

    public static boolean isValidType(Type type, SymbolTable table) {
        if (type.getName().equals("int"))
            return true;
        if (type.getName().equals("String"))
            return true;
        if (type.isArray())
            return false;
        if (type.getName().equals("boolean"))
            return true;
        if (type.getName().equals(table.getClassName()))
            return true;
        if (isInImports(type.getName(), table))
            return true;
        return false;
    }

    public static boolean isInImports(String name, SymbolTable table) {
        return table.getImports().stream().map(x -> {
            var splitImport = x.split("\\.");
            return splitImport[splitImport.length - 1];
        }).anyMatch(x -> x.equals(name));
    }

}
