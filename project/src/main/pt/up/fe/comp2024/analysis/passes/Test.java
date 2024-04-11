package pt.up.fe.comp2024.analysis.passes;

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
import java.util.Set;
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

    protected void visitAssignStatement(JmmNode stmt, SymbolTable table) {

        var left = TypeUtils.visitVariableReferenceExpression(stmt.get("name"), table, stmt);
        var right = TypeUtils.getExprType(stmt.getChild(0), table);
        if (right == null)
            return;

        if (left.equals(right)) {
            return;
        }

        if (left.isArray()) {
            addNewReport("Assign Statement : Assignment of array variable wrong", stmt);
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

        addNewReport("Assign Statement : Assignment of not array variable wrong", stmt);
    }

    protected void visitStatement(JmmNode stmt, SymbolTable table) {
        switch (stmt.getKind()) {
            case ("MultiStmt"):
                stmt.getChildren().forEach(stm -> visitStatement(stm, table));
                break;
            case ("IfStmt"): {
                Type type = TypeUtils.getExprType(stmt.getChild(0), table);
                if (!type.getName().equals("boolean") || type.isArray()) {
                    addNewReport("Not boolean expression in if", stmt.getChild(0));
                }
                visitStatement(stmt.getChild(1), table);
                visitStatement(stmt.getChild(2), table);
                break;
            }
            case ("WhileStmt"): {
                Type type = TypeUtils.getExprType(stmt.getChild(0), table);
                if (!type.getName().equals("boolean") || type.isArray()) {
                    addNewReport("Not boolean expression in while", stmt.getChild(0));
                }
                visitStatement(stmt.getChild(1), table);
                break;
            }
            case ("VarStmt"):
                TypeUtils.getExprType(stmt.getChild(0), table);
                break;
            case ("AssignStmt"):
                visitAssignStatement(stmt, table);
                break;
            case ("AssignStmtArray"): {
                var array = TypeUtils.getExprType(stmt.getChild(0), table);
                var index = TypeUtils.getExprType(stmt.getChild(1), table);
                var value = TypeUtils.getExprType(stmt.getChild(2), table);
                if (!array.isArray()) {
                    addNewReport("Array index of non array", stmt.getChild(0));
                    return;
                }
                if (index.isArray() || !index.getName().equals("int")) {
                    addNewReport("Array index must be an integer", stmt.getChild(1));
                    return;
                }
                if (value.isArray()) {
                    addNewReport("Array assignment value must not be array", stmt.getChild(2));
                    return;
                }
                if (table.getSuper() != null &&
                        table.getSuper().equals(array.getName()) &&
                        table.getClassName().equals(value.getName())) {
                    return;
                }
                if (!array.getName().equals(value.getName())) {
                    addNewReport("Array assignment value must be of the same tipe as array", stmt.getChild(2));
                    return;
                }
                break;
            }
            case ("ReturnStmt"): {
                System.out.println("oi");
                System.out.println(stmt.getChild(0));
                Type typeExpr = TypeUtils.getExprType(stmt.getChild(0), table);
                Optional<Type> typeReturn = table.getReturnTypeTry(currentMethod);
                if (typeReturn.isPresent()) {
                    if (typeExpr != null && !typeExpr.equals(typeReturn.get()))
                        addNewReport("Return Statement : wrong type", stmt.getChild(0));
                }
                break;
            }
        }
    }

    protected void visitMethods(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        TypeUtils.currentMethod = currentMethod;
        var params = table.getParameters(currentMethod);
        Set<String> paramsSet = Set.copyOf(params.stream().map(field -> field.getName()).toList());
        if (paramsSet.size() != params.size()) {
            addNewReport("Duplicate param names in method declaration", method);
        }
        var locals = table.getLocalVariables(currentMethod);
        Set<String> localsSet = Set
                .copyOf(locals.stream().map(field -> field.getName()).toList());
        if (localsSet.size() != locals.size()) {
            addNewReport("Duplicate local variable names in method declaration", method);
        }
        for (int i = 0; i < params.size() - 1; i++) {
            if (TypeUtils.isVarArgs(params.get(i).getType())) {
                addNewReport("VarArgs : VarArgs are only valid as the last argument",
                        method);
            }
        }
        method.getChildren("Stmt").forEach(stmt -> visitStatement(stmt, table));
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        TypeUtils.reports = this.reports;
        Set<String> fieldsSet = Set.copyOf(table.getFields().stream().map(field -> field.getName()).toList());
        if (fieldsSet.size() != table.getFields().size()) {
            addNewReport("Duplicate field names in class declaration", root);
        }
        root.getChildren(Kind.CLASS_DECL).get(0).getChildren(Kind.METHOD_DECL).stream()
                .forEach(method -> visitMethods(method, table));
        return reports;
    }
}
