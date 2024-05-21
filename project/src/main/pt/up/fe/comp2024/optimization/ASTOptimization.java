package pt.up.fe.comp2024.optimization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2024.ast.Kind;

class Value {
    int i;
    boolean b;
    boolean isInt;

    Value(int i) {
        isInt = true;
        this.i = i;
    }

    Value(boolean b) {
        isInt = false;
        this.b = b;
    }

    @Override
    public String toString() {
        return "" + (isInt ? i : b);
    }

    public boolean equals(Value other) {
        if (isInt != other.isInt)
            return false;
        if (isInt)
            return i == other.i;
        return b == other.b;
    }
}

public class ASTOptimization {
    public final JmmNode rootNode;
    public final SymbolTable table;
    private boolean changed = true;
    private String currentMethod;

    private Map<String, Value> constants = new HashMap<>();

    public ASTOptimization(JmmNode rootNode, SymbolTable symbolTable) {
        this.rootNode = rootNode;
        this.table = symbolTable;
    }

    public void optimize() {
        while (changed) {
            changed = false;
            for (var method : rootNode.getChildren(Kind.CLASS_DECL).get(0).getChildren(Kind.METHOD_DECL)) {
                visitMethod(method);
            }
        }
    }

    public void visitMethod(JmmNode method) {
        currentMethod = method.get("name");
        constFoldMethod(method);
        constPropMethod(method);
    }

    public void constPropMethod(JmmNode method) {
        constants.clear();
        for (var stmt : method.getChildren()) {
            constPropStatement(stmt);
        }
    }

    public void constPropStatement(JmmNode stmt) {
        switch (stmt.getKind()) {
            case ("MultiStmt"):
                stmt.getChildren().forEach((stm) -> constPropStatement(stm));
                break;
            case ("IfStmt"): {
                constPropExpr(stmt.getChild(0));
                var savedConstants = new HashMap<>(constants);
                constPropStatement(stmt.getChild(1));
                var ifConstants = new HashMap<>(constants);

                constants = new HashMap<>(savedConstants);
                constPropStatement(stmt.getChild(2));

                var toRemove = new ArrayList<String>();
                for (var entry : constants.entrySet()) {
                    if (!ifConstants.containsKey(entry.getKey())) {
                        toRemove.add(entry.getKey());
                    } else if (!entry.getValue().equals(ifConstants.get(entry.getKey()))) {
                        toRemove.add(entry.getKey());
                    }
                }
                for (String name : toRemove) {
                    constants.remove(name);
                }

                break;
            }
            case ("WhileStmt"): {
                // TODO
                constants.clear();
                constPropStatement(stmt.getChild(1));
                constants.clear();
                break;
            }
            case ("VarStmt"):
                constPropExpr(stmt.getChild(0));
                break;
            case ("AssignStmt"): {
                constPropExpr(stmt.getChild(0));
                var value = stmt.getChild(0);
                String name = stmt.get("name");
                constants.remove(name);
                var vars = table.getLocalVariables(currentMethod);
                for (var v : vars) {
                    if (v.getName().equals(name)) {
                        var type = v.getType();
                        if (type.isArray())
                            break;
                        switch (type.getName()) {
                            case "int":
                                if (value.getKind().equals("IntegerLiteral")) {
                                    constants.put(name, new Value(Integer.parseInt(value.get("value"))));
                                }
                                break;
                            case "boolean":
                                if (value.getKind().equals("VarRefExpr")
                                        && (value.get("name").equals("true") || value.get("name").equals("false"))) {
                                    constants.put(name, new Value(value.get("name").equals("true")));
                                }
                                break;
                        }
                    }
                }
                break;
            }
            case ("AssignStmtArray"): {
                constPropExpr(stmt.getChild(0));
                constPropExpr(stmt.getChild(1));
                break;
            }
            case ("ReturnStmt"): {
                constPropExpr(stmt.getChild(0));
                break;
            }
        }
    }

    public void constPropExpr(JmmNode expr) {
        switch (expr.getKind()) {
            case "VarRefExpr":
                String name = expr.get("name");
                if (constants.containsKey(name)) {
                    changed = true;
                    var value = constants.get(name);
                    var newNode = new JmmNodeImpl(value.isInt ? "IntegerLiteral" : "VarRefExpr");
                    newNode.put(value.isInt ? "value" : "name", value.toString());
                    expr.replace(newNode);
                }
                break;
            case "IntegerLiteral", "FieldAccessExpr", "NewExpr":
                break;
            case "ArrayExpr", "FuncExpr", "SelfFuncExpr": {
                for (var exp : expr.getChildren()) {
                    constPropExpr(exp);
                }
                break;
            }
            case "NewArrayExpr", "UnaryExpr": {
                constPropExpr(expr.getChild(0));
                break;
            }
            case "BinaryExpr", "ArrayAccessExpr": {
                constPropExpr(expr.getChild(0));
                constPropExpr(expr.getChild(1));
                break;
            }
        }
    }

    public void constFoldMethod(JmmNode method) {
        for (var stmt : method.getChildren()) {
            constFoldStatement(stmt);
        }
    }

    public void constFoldStatement(JmmNode stmt) {
        switch (stmt.getKind()) {
            case ("MultiStmt"):
                stmt.getChildren().forEach(stm -> constFoldStatement(stm));
                break;
            case ("IfStmt"): {
                constFoldExpr(stmt.getChild(0));
                constFoldStatement(stmt.getChild(1));
                constFoldStatement(stmt.getChild(2));
                break;
            }
            case ("WhileStmt"): {
                constFoldExpr(stmt.getChild(0));
                constFoldStatement(stmt.getChild(1));
                break;
            }
            case ("VarStmt"):
                constFoldExpr(stmt.getChild(0));
                break;
            case ("AssignStmt"):
                constFoldExpr(stmt.getChild(0));
                break;
            case ("AssignStmtArray"): {
                constFoldExpr(stmt.getChild(0));
                constFoldExpr(stmt.getChild(1));
                break;
            }
            case ("ReturnStmt"): {
                constFoldExpr(stmt.getChild(0));
                break;
            }
        }
    }

    public void constFoldExpr(JmmNode expr) {
        switch (expr.getKind()) {
            case "IntegerLiteral", "VarRefExpr", "FieldAccessExpr", "NewExpr":
                break;
            case "ParenExpr": {
                changed = true;
                constFoldExpr(expr.getChild(0));
                expr.replace(expr.getChild(0));
                break;
            }
            case "ArrayExpr", "FuncExpr", "SelfFuncExpr": {
                for (var exp : expr.getChildren()) {
                    constFoldExpr(exp);
                }
                break;
            }
            case "NewArrayExpr": {
                constFoldExpr(expr.getChild(0));
                break;
            }
            case "UnaryExpr": {
                constFoldExpr(expr.getChild(0));
                var child = expr.getChild(0);
                if (child.getKind().equals("VarRefExpr"))
                    if (child.get("name").equals("true")) {
                        changed = true;

                        var newNode = new JmmNodeImpl("VarRefExpr");
                        newNode.put("name", "false");
                        expr.replace(newNode);

                    } else if (child.get("name").equals("false")) {
                        changed = true;

                        var newNode = new JmmNodeImpl("VarRefExpr");
                        newNode.put("name", "true");
                        expr.replace(newNode);

                    }
                break;
            }
            case "BinaryExpr": {
                constFoldExpr(expr.getChild(0));
                constFoldExpr(expr.getChild(1));
                var left = expr.getChild(0);
                var right = expr.getChild(1);

                boolean leftIsInt = left.getKind().equals("IntegerLiteral");
                boolean rightIsInt = right.getKind().equals("IntegerLiteral");
                int leftValue = 0;
                int rightValue = 0;
                if (leftIsInt)
                    leftValue = Integer.parseInt(left.get("value"));
                if (rightIsInt)
                    rightValue = Integer.parseInt(right.get("value"));
                boolean leftIsVar = left.getKind().equals("VarRefExpr");
                String leftName = "";
                if (leftIsVar)
                    leftName = left.get("name");
                boolean rightIsVar = right.getKind().equals("VarRefExpr");
                String rightName = "";
                if (rightIsVar)
                    rightName = right.get("name");
                boolean leftIsBool = leftIsVar && (leftName.equals("true") || leftName.equals("false"));
                boolean rightIsBool = rightIsVar && (rightName.equals("true") || rightName.equals("false"));
                boolean leftBool = false;
                boolean rightBool = false;
                if (leftIsBool)
                    leftBool = leftName.equals("true");
                if (rightIsBool)
                    rightBool = rightName.equals("true");

                switch (expr.get("op")) {
                    case "+":
                        if (leftIsInt && rightIsInt) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "" + (leftValue + rightValue));
                            expr.replace(newNode);
                        } else if (leftIsInt && leftValue == 0) {
                            changed = true;
                            expr.replace(right);
                        } else if (rightIsInt && rightValue == 0) {
                            changed = true;
                            expr.replace(left);
                        }
                        break;
                    case "-":
                        if (leftIsInt && rightIsInt) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "" + (leftValue - rightValue));
                            expr.replace(newNode);
                        } else if (rightIsInt && rightValue == 0) {
                            changed = true;
                            expr.replace(left);
                        } else if (leftIsVar && rightIsVar && leftName.equals(rightName)) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "0");
                            expr.replace(newNode);
                        }
                        break;
                    case "*":
                        if (leftIsInt && rightIsInt) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "" + (leftValue * rightValue));
                            expr.replace(newNode);
                        } else if ((leftIsInt && leftValue == 0) || (rightIsInt && rightValue == 0)) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "0");
                            expr.replace(newNode);
                        } else if (leftIsInt && leftValue == 1) {
                            changed = true;
                            expr.replace(right);
                        } else if (rightIsInt && rightValue == 1) {
                            changed = true;
                            expr.replace(left);
                        }
                        break;
                    case "/":
                        if (leftIsInt && rightIsInt && rightValue != 0) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "" + (leftValue / rightValue));
                            expr.replace(newNode);
                        } else if (leftIsInt && leftValue == 0) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "0");
                            expr.replace(newNode);
                        } else if (rightIsInt && rightValue == 1) {
                            changed = true;
                            expr.replace(left);
                        } else if (leftIsVar && rightIsVar && leftName.equals(rightName)) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "1");
                            expr.replace(newNode);
                        }
                        break;
                    case "<":
                        if (leftIsInt && rightIsInt) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", (leftValue < rightValue) ? "true" : "false");
                            expr.replace(newNode);
                        } else if (leftIsVar && rightIsVar && leftName.equals(rightName)) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", "false");
                            expr.replace(newNode);
                        }
                        break;
                    case "&&":
                        if (leftIsBool && rightIsBool) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", (leftBool && rightBool) ? "true" : "false");
                            expr.replace(newNode);
                        } else if (leftName.equals(rightName)) {
                            changed = true;
                            expr.replace(left);
                        } else if (leftIsBool && leftBool) {
                            changed = true;
                            expr.replace(right);
                        } else if (leftIsBool && !leftBool) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", "false");
                            expr.replace(newNode);
                        } else if (rightIsBool && rightBool) {
                            changed = true;
                            expr.replace(left);
                        } else if (rightIsBool && !rightBool) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", "false");
                            expr.replace(newNode);
                        }
                        break;
                }
                break;
            }
            case "ArrayAccessExpr": {
                constFoldExpr(expr.getChild(0));
                constFoldExpr(expr.getChild(1));
            }
        }
    }

}
