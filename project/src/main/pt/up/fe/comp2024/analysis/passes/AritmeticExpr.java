package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class AritmeticExpr extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");
        JmmNode first = binaryExpr.getChild(0);
        JmmNode second = binaryExpr.getChild(1);

        boolean firstBool = false;
        boolean secondBool = false;

        while (first.getKind().equals("ParenExpr")){
            first = first.getChild(0);
        }
        while (second.getKind().equals("ParenExpr")){
            second = second.getChild(0);
        }

        if(first.getChildren().isEmpty()) {
            if (checkNodeInt(first, table)) firstBool = true;
        }
        else {
            if(first.getKind().equals("BinaryExpr")) {
                int sizeReports = getReports().size();
                visitBinaryExpr(first, table);
                if (sizeReports == getReports().size()) firstBool = true;
            }
        }

        if(second.getChildren().isEmpty()) {
            if (checkNodeInt(second, table)) secondBool = true;
        }
        else {
            if(second.getKind().equals("BinaryExpr")) {
                int sizeReports = getReports().size();
                visitBinaryExpr(second, table);
                if (sizeReports == getReports().size()) secondBool = true;
            }
        }

        if(firstBool && secondBool) return null;

        System.out.println("first");
        System.out.println("second");
        System.out.println(firstBool);
        System.out.println(secondBool);
        System.out.println(first);
        System.out.println(second);
        System.out.println(second.getChildren());

        // Create error report
        var message = String.format("Not aritmetic");
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(binaryExpr),
                NodeUtils.getColumn(binaryExpr),
                message,
                null));

        return null;
    }

    // não vê elementos de uma lista tipo int a[0]
    private boolean checkNodeInt(JmmNode var, SymbolTable table) {
        if(var.getKind().equals("IntegerLiteral")) return true;

        if(var.getKind().equals("VarRefExpr")){
            var name = var.get("name");

            if (table.getFields().stream()
                    .anyMatch(param -> param.getName().equals(name) && param.getType().equals("int") && !param.getType().isArray())) return true;

            if (table.getParameters(currentMethod).stream()
                    .anyMatch(param -> param.getName().equals(name) && param.getType().getName().equals("int") && !param.getType().isArray())) return true;

            if (table.getLocalVariables(currentMethod).stream()
                    .anyMatch(varDecl -> varDecl.getName().equals(name) && varDecl.getType().getName().equals("int") && !varDecl.getType().isArray())) return true;
        }
        return false;
    }
}