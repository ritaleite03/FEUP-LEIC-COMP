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
import pt.up.fe.comp2024.utils.ReservedWords;

import java.util.*;

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
        if (left == null)
            return;
        if (right == null)
            return;

        if (left.equals(right)) {
            return;
        }

        // if (left.isArray()) {
        // addNewReport("Assign Statement : Assignment of array variable wrong", stmt);
        // }

        boolean leftIsImport = TypeUtils.isInImports(left.getName(), table);
        // boolean leftIsMain = table.getClassName().equals(left.getName());
        boolean leftIsSuper = table.getSuper() != null && table.getSuper().equals(left.getName());
        // boolean rightIsImport = table.getImports().contains(right.getName());
        boolean rightIsMain = table.getClassName().equals(right.getName());
        // boolean rightIsSuper = table.getSuper() != null &&
        // table.getSuper().equals(right.getName());

        if (leftIsSuper && rightIsMain)
            return;
        if (leftIsImport && !rightIsMain)
            return;

        addNewReport("Assign Statement : Assignment of not array variable wrong", stmt);
    }

    protected void visitStatement(JmmNode stmt, SymbolTable table) {
        for (int i = 0; i < stmt.getChildren().size(); i++) {
            var stmtChild = stmt.getChild(i);
            if (stmtChild.getKind().equals("ReturnStmt") && i < stmt.getChildren().size() - 1) {
                addNewReport("Error : Return not the last stmt on block", stmt);
            }
        }

        switch (stmt.getKind()) {
            case ("MultiStmt"):
                stmt.getChildren().forEach(stm -> visitStatement(stm, table));
                break;
            case ("IfStmt"): {
                Type type = TypeUtils.getExprType(stmt.getChild(0), table);
                if (type != null && (!type.getName().equals("boolean") || type.isArray())) {
                    addNewReport("Not boolean expression in if", stmt.getChild(0));
                }
                visitStatement(stmt.getChild(1), table);
                visitStatement(stmt.getChild(2), table);
                break;
            }
            case ("WhileStmt"): {
                Type type = TypeUtils.getExprType(stmt.getChild(0), table);
                if (type != null && (!type.getName().equals("boolean") || type.isArray())) {
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
                var array = TypeUtils.visitVariableReferenceExpression(stmt.get("name"), table, stmt);
                var index = TypeUtils.getExprType(stmt.getChild(0), table);
                var value = TypeUtils.getExprType(stmt.getChild(1), table);
                if (array == null)
                    return;
                if (!array.isArray()) {
                    addNewReport("Array index of non array", stmt);
                    return;
                }
                if (index == null)
                    return;
                if (index.isArray() || !index.getName().equals("int")) {
                    addNewReport("Array index must be an integer", stmt.getChild(0));
                    return;
                }
                if (value == null)
                    return;
                if (value.isArray()) {
                    addNewReport("Array assignment value must not be array", stmt.getChild(1));
                    return;
                }
                if (table.getSuper() != null &&
                        table.getSuper().equals(array.getName()) &&
                        table.getClassName().equals(value.getName())) {
                    return;
                }
                if (TypeUtils.isInImports(value.getName(), table) && !table.getClassName().equals(value.getName()))
                    return;
                if (!array.getName().equals(value.getName())) {
                    addNewReport("Array assignment value must be of the same tipe as array", stmt.getChild(1));
                    return;
                }
                break;
            }
            case ("ReturnStmt"): {
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
        TypeUtils.isStatic = NodeUtils.getBooleanAttribute(method, "isStatic", "false");
        var params = table.getParameters(currentMethod);
        System.out.println(currentMethod + ":" + TypeUtils.isStatic);
        if (currentMethod.equals("main")) {
            if (params.size() != 1 || !params.get(0).getType().getName().equals("String")
                    || !params.get(0).getType().isArray()) {
                addNewReport("Error: Main method does not have only String[] as params", method);
            }
            if (!method.get("isStatic").equals("true")) {
                addNewReport("Error: Main method not static", method);
            }
            if (!method.get("isPublic").equals("true")) {
                addNewReport("Error: Main method not public", method);
            }
            if (!method.getChildren().get(0).get("name").equals("void")) {
                addNewReport("Error: Main method not void", method);
            }
        } else {
            if (method.get("isStatic").equals("true")) {
                addNewReport("Error: method not main cannot be static", method);
            }
        }

        Set<String> paramsSet = Set.copyOf(params.stream().map(field -> {
            String name = field.getName();
            if (ReservedWords.isReservedWord(name)) {
                addNewReport("Error: The word '" + name + "' is a reserved word and cannot be used as an identifier",
                        method);
            }
            return name;
        }).toList());

        if (paramsSet.size() != params.size()) {
            addNewReport("Duplicate param names in method declaration", method);
        }
        var locals = table.getLocalVariables(currentMethod);
        Set<String> localsSet = Set.copyOf(locals.stream().map(local -> {
            String name = local.getName();
            if (ReservedWords.isReservedWord(name)) {
                addNewReport("Error: The word '" + name + "' is a reserved word and cannot be used as an identifier",
                        method);
            }
            if (!TypeUtils.isValidType(local.getType(), table)) {
                addNewReport("Invalid type : " + local.getType(), method);
            }
            return name;
        }).toList());
        if (localsSet.size() != locals.size()) {
            addNewReport("Duplicate local variable names in method declaration", method);
        }
        for (int i = 0; i < params.size(); i++) {
            if (i < params.size() - 1 && TypeUtils.isVarArgs(params.get(i).getType())) {
                addNewReport("VarArgs : VarArgs are only valid as the last argument",
                        method);
            }
            if (!TypeUtils.isValidType(params.get(i).getType(), table)) {
                addNewReport("Invalid type : " + params.get(i).getType(), method);
            }
        }
        for (int i = 0; i < method.getChildren().size(); i++) {
            var stmt = method.getChild(i);
            if (stmt.getKind().equals("ReturnStmt") && i < method.getChildren().size() - 1) {
                addNewReport("Error : Return not the last stmt", method);
            }
            visitStatement(stmt, table);
        }
        if (!method.getChild(method.getChildren().size() - 1).getKind().equals("ReturnStmt")
                && !method.getChildren().get(0).get("name").equals("void")) {
            addNewReport("Error : method should have a return", method);
        }
    }

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        TypeUtils.reports = this.reports;
        Set<String> fieldsSet = Set.copyOf(table.getFields().stream().map(field -> {
            String name = field.getName();
            if (ReservedWords.isReservedWord(name)) {
                addNewReport("Error: The word '" + name + "' is a reserved word and cannot be used as an identifier",
                        root);
            }
            if (!TypeUtils.isValidType(field.getType(), table)) {
                addNewReport("Invalid type : " + field.getType(), root);
            }
            return name;
        }).toList());
        if (fieldsSet.size() != table.getFields().size()) {
            addNewReport("Duplicate field names in class declaration", root);
        }

        Set<String> importSet = new HashSet<>();
        var imports = table.getImports();
        // importSet.add(table.getClassName());
        for (int i = 0; i < imports.size(); i++) {
            var splitImport = imports.get(i).split("\\.");
            importSet.add(splitImport[splitImport.length - 1]);
        }

        if (importSet.size() != imports.size()) {
            addNewReport("Error: Duplicated Imports", root);
        }

        Set<String> methodSet = new HashSet<>();
        var methods = table.getMethods();
        for (int i = 0; i < methods.size(); i++) {
            methodSet.add(methods.get(i));
        }

        if (methodSet.size() != methods.size()) {
            addNewReport("Error: Duplicated Methods", root);
        }

        root.getChildren(Kind.CLASS_DECL).get(0).getChildren(Kind.METHOD_DECL).stream()
                .forEach(method -> visitMethods(method, table));
        return reports;
    }
}
