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

    protected void visitAssignStatement(JmmNode expr, SymbolTable table) {

        var left = TypeUtils.visitVariableReferenceExpression(expr.get("name"), table, expr);
        var right = TypeUtils.getExprType(expr.getChild(0), table);
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

    protected void visitMethods(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        TypeUtils.currentMethod = currentMethod;
        var params = table.getParameters(currentMethod);
        for (int i = 0; i < params.size() - 1; i++) {
            if (TypeUtils.isVarArgs(params.get(i).getType())) {
                addNewReport("VarArgs : VarArgs are only valid as the last argument",
                        method);
            }
        }
        method.getChildren("VarStmt").forEach(stmt -> TypeUtils.getExprType(stmt.getChild(0), table));
        method.getChildren("AssignStmt").forEach(stmt -> visitAssignStatement(stmt, table));
        method.getChildren("IfStmt").forEach(stmt -> TypeUtils.getExprType(stmt, table));
        method.getChildren("WhileStmt").forEach(stmt -> TypeUtils.getExprType(stmt, table));
        method.getChildren("ReturnStmt").forEach(stmt -> {
            System.out.println("oi");
            System.out.println(stmt.getChild(0));
            Type typeExpr = TypeUtils.getExprType(stmt.getChild(0), table);
            Optional<Type> typeReturn = table.getReturnTypeTry(method.get("name"));
            if (typeReturn.isPresent()) {
                if (typeExpr != null && !typeExpr.equals(typeReturn.get()))
                    addNewReport("Return Statement : wrong type", stmt.getChild(0));
            }
        });
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        TypeUtils.reports = this.reports;
        root.getChildren(Kind.CLASS_DECL).get(0).getChildren(Kind.METHOD_DECL).stream()
                .forEach(method -> visitMethods(method, table));
        return reports;
    }
}
