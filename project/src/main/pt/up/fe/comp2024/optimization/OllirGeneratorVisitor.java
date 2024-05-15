package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";

    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;
    private String currentMethod;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }

    @Override
    protected void buildVisitor() {
        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT, this::visitImport);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(ASSIGN_STMT_ARRAY, this::visitAssignStmtArray);
        addVisit(VAR_STMT, this::visitVarStmt);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(MULTI_STMT,this::visitMultiStmt);
        addVisit(WHILE_STMT,this::visitWhileStmt);
        setDefaultVisit(this::defaultVisit);
    }

    private String visitAssignStmt(JmmNode node, Void unused) {

        var lhs = node.get("name");
        Type thisType = TypeUtils.visitVariableReferenceExpression(lhs, table, node);
        String typeString = OptUtils.toOllirType(thisType);

        var rhs = exprVisitor.visit(node.getJmmChild(0), new InferType(thisType));

        StringBuilder code = new StringBuilder();
;

        // code to compute the children
        code.append(rhs.getComputation());

        var isLocalList = table.getLocalVariables(currentMethod).stream().filter(local -> local.getName().equals(
                lhs))
                .toList();
        var isParamList = table.getParameters(currentMethod).stream().filter(param -> param.getName().equals(
                lhs))
                .toList();
        var isFieldList = table.getFields().stream().filter(field -> field.getName().equals(lhs)).toList();

        if (isLocalList.isEmpty() && isParamList.isEmpty() && !isFieldList.isEmpty()) {
            code.append("putfield(this, ");
            code.append(lhs);
            code.append(typeString);
            code.append(", ");
            code.append(rhs.getCode());
            code.append(").V;\n");
            return code.toString();
        }

        // code to compute self
        // statement has type of lhs
        code.append(lhs);
        code.append(typeString);
        code.append(SPACE);
        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);
        code.append(rhs.getCode());
        code.append(END_STMT);

        return code.toString();
    }

    private String visitAssignStmtArray(JmmNode node, Void unused) {

        String lhsName = node.get("name");
        Type thisType = TypeUtils.visitVariableReferenceExpression(lhsName, table, node);
        Type thisType2 = new Type(thisType.getName(), false);
        String typeString = OptUtils.toOllirType(thisType2);

        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1), new InferType(thisType2));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        var isLocalList = table.getLocalVariables(currentMethod).stream().filter(local -> local.getName().equals(
                        lhsName))
                .toList();
        var isParamList = table.getParameters(currentMethod).stream().filter(param -> param.getName().equals(
                        lhsName))
                .toList();
        var isFieldList = table.getFields().stream().filter(field -> field.getName().equals(lhsName)).toList();

        if (isLocalList.isEmpty() && isParamList.isEmpty() && !isFieldList.isEmpty()) {
            code.append("putfield(this, ");
            code.append(lhs.getCode());
            code.append(typeString);
            code.append(", ");
            code.append(rhs.getCode());
            code.append(").V;\n");
            return code.toString();
        }

        // code to compute self
        // statement has type of lhs
        code.append(lhsName);
        code.append("[");
        code.append(lhs.getCode());
        code.append("]");
        code.append(typeString);
        code.append(SPACE);
        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);
        code.append(rhs.getCode());
        code.append(END_STMT);

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        // number used for the labels
        var numberLabel = OptUtils.getNextTempNum();
        // condition
        var expression = exprVisitor.visit(node.getJmmChild(0), new InferType(new Type("boolean",false)));
        // code block of the loop
        var body = this.visit(node.getJmmChild(1));

        String condLabel = "whileCond" + numberLabel;
        String loopLabel = "whileLoop" + numberLabel;
        String endLabel = "whileEnd" + numberLabel;

        code.append(condLabel);
        code.append(":\n");
        code.append(expression.getComputation());

        code.append("if");
        code.append(SPACE);
        code.append("(");
        code.append(expression.getCode());
        code.append(")");
        code.append(SPACE);
        code.append("goto");
        code.append(SPACE);
        code.append(loopLabel);
        code.append(END_STMT);
        code.append("goto");
        code.append(SPACE);
        code.append(endLabel);
        code.append(END_STMT);

        code.append(loopLabel);
        code.append(":\n");
        code.append(body);
        code.append("goto");
        code.append(SPACE);
        code.append(condLabel);
        code.append(END_STMT);

        code.append(endLabel);
        code.append(":\n");

        return code.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused){
        // condition
        var expression = exprVisitor.visit(node.getJmmChild(0), new InferType(new Type("boolean",false)));
        // number used for the labels
        var numberLabel = OptUtils.getNextTempNum();
        String initLabel = "if" + numberLabel;
        String endLabel = "endif" + numberLabel;

        var ifTrue = this.visit(node.getJmmChild(1)); // code block if true
        var ifFalse = this.visit(node.getJmmChild(2)); // code block if false

        StringBuilder code = new StringBuilder();
        code.append(expression.getComputation());
        code.append("if");
        code.append(SPACE);
        code.append("(");
        code.append(expression.getCode());
        code.append(")");
        code.append(SPACE);
        code.append("goto");
        code.append(SPACE);
        code.append(initLabel);
        code.append(END_STMT);

        code.append(ifFalse);
        code.append("goto");
        code.append(SPACE);
        code.append(endLabel);
        code.append(END_STMT);

        code.append(initLabel);
        code.append(":\n");
        code.append(ifTrue);

        code.append(endLabel);
        code.append(":\n");

        return code.toString();
    }

    private String visitMultiStmt(JmmNode node, Void unused){
        System.out.println("visitMultiStmt");
        StringBuilder code = new StringBuilder();
        for(int i = 0; i < node.getChildren().size(); i++){
            code.append(this.visit(node.getJmmChild(i)));
        }
        return code.toString();
    }

    private String visitVarStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var expression = exprVisitor.visit(node.getJmmChild(0), new InferType(null, false));

        // code to compute the children
        code.append(expression.getComputation());
        code.append(NL);
        return code.toString();
    }

    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0), new InferType(retType));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");
        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");
        if (isPublic) {
            code.append("public ");
        }
        boolean isStatic = NodeUtils.getBooleanAttribute(node, "isStatic", "false");
        if (isStatic) {
            code.append("static ");
        }
        // name
        currentMethod = node.get("name");
        code.append(currentMethod);
        exprVisitor.currentMethod = currentMethod;
        TypeUtils.currentMethod = currentMethod;
        TypeUtils.isStatic = NodeUtils.getBooleanAttribute(node, "isStatic", "false");

        // param
        int paramsSize = 0;
        List<String> params = new ArrayList<>();
        while (true) {
            if (paramsSize + 1 == node.getNumChildren())
                break;
            var paramNode = node.getJmmChild(paramsSize + 1);
            if (!paramNode.getKind().equals("Param"))
                break;
            params.add(visit(paramNode));
            paramsSize++;
        }
        code.append("(" + String.join(",", params) + ")");
        // type
        var retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);
        // rest of its children stmts
        var afterParam = paramsSize + 1;
        for (int i = afterParam; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            var childCode = visit(child);
            code.append(childCode);
        }

        if (node.getChildren().stream().filter(child -> child.getKind().equals("ReturnStmt")).count() == 0) {
            code.append("ret.V ;\n");
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());
        if (table.getSuper() != null) {
            code.append(" extends ");
            code.append(table.getSuper());
        }
        code.append(L_BRACKET);

        code.append(NL);

        for (Symbol field : table.getFields()) {
            code.append(".field public ");
            code.append(field.getName());
            code.append(OptUtils.toOllirType(field.getType()));
            code.append(";\n");
        }

        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    private String visitImport(JmmNode node, Void unused) {
        return "import " + String.join(".", node.getObjectAsList("name", String.class)) + ";\n";
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }
        return "";
    }
}
