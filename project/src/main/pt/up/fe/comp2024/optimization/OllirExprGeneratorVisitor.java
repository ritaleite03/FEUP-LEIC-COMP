package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

import java.util.ArrayList;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<InferType, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;
    public String currentMethod;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(PAREN_EXPR, this::visitParenExpr);
        addVisit(FUNC_EXPR, this::visitFunctionCall);
        addVisit(SELF_FUNC_EXPR, this::visitSelfFunctionCall);
        addVisit(NEW_EXPR, this::visitNewExpr);
        addVisit(NEW_ARRAY_EXPR, this::visitNewArrayExpr);
        addVisit(FIELD_ACCESS_EXPR, this::visitFieldAccessExpr);

        addVisit("ArrayAccessExpr", this::visitArrayAccessExpr);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitInteger(JmmNode node, InferType expected) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitParenExpr(JmmNode node, InferType expected) {
        return visit(node.getChild(0), expected);
    }

    private OllirExprResult visitNewExpr(JmmNode node, InferType expected) {
        var name = node.get("name");
        var tmp = OptUtils.getTemp();
        var code = tmp + "." + name;
        var computation = new StringBuilder();
        computation.append(code);
        computation.append(" :=.");
        computation.append(name);
        computation.append(" new(");
        computation.append(name);
        computation.append(").");
        computation.append(name);
        computation.append(";\ninvokespecial(");
        computation.append(code);
        computation.append(",\"<init>\").V;\n");
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewArrayExpr(JmmNode node, InferType expected) {
        Type resType = typeOrExpected(TypeUtils.getExprType(node, table), expected);
        String resOllirType = OptUtils.toOllirType(resType);
        var tmp = OptUtils.getTemp();
        var code = tmp + resOllirType ;
        var computation = new StringBuilder();
        computation.append(code);
        computation.append(SPACE);
        computation.append(ASSIGN);
        computation.append(resOllirType);
        computation.append(SPACE);
        computation.append("new(array,");
        computation.append(SPACE);
        computation.append(node.getChild(0).get("value"));
        computation.append(".i32)");
        computation.append(resOllirType);
        computation.append(END_STMT);
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitBinExpr(JmmNode node, InferType expected) {
        StringBuilder computation = new StringBuilder();

        if (node.get("op").equals("&&")) {

            String code = OptUtils.getTemp() + OptUtils.toOllirType(new Type("boolean", false));

            var numberIf = OptUtils.getNextTempNum();

            var lhs = visit(node.getJmmChild(0), new InferType(new Type("boolean", false)));
            var rhs = visit(node.getJmmChild(1), new InferType(new Type("boolean", false)));

            String initIf = "if" + numberIf;
            String endIf = "end" + numberIf;

            // computation of left side
            computation.append(lhs.getComputation());
            // see if left side is true
            computation.append("if");
            computation.append(SPACE);
            computation.append("(");
            computation.append(lhs.getCode());
            computation.append(")");
            computation.append(SPACE);
            // if true analyse right side
            computation.append("goto");
            computation.append(SPACE);
            computation.append(initIf);
            computation.append(END_STMT);
            // else is false
            computation.append(code);
            computation.append(SPACE);
            computation.append(":=.bool false.bool");
            computation.append(END_STMT);
            // assign the right side
            computation.append(initIf);
            computation.append(":\n");
            computation.append(code);
            computation.append(SPACE);
            computation.append(ASSIGN);
            computation.append(".bool");
            computation.append(SPACE);
            computation.append(rhs.getCode());
            computation.append(END_STMT);
            computation.append("goto");
            computation.append(SPACE);
            computation.append(endIf);
            computation.append(END_STMT);

            computation.append(endIf);
            computation.append(":\n");
            return new OllirExprResult(code, computation);
        }
        var lhs = visit(node.getJmmChild(0), new InferType(new Type("int", false)));
        var rhs = visit(node.getJmmChild(1), new InferType(new Type("int", false)));


        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = typeOrExpected(TypeUtils.getExprType(node, table), expected);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, InferType expected) {

        var s = visit(node.getJmmChild(0), new InferType(new Type("boolean", false)));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(s.getComputation());

        // code to compute self
        Type resType = typeOrExpected(TypeUtils.getExprType(node, table), expected);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE);

        computation.append(node.get("op")).append(resOllirType).append(SPACE)
                .append(s.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitVarRef(JmmNode node, InferType expected) {

        var id = node.get("name");
        Type type = typeOrExpected(TypeUtils.getExprType(node, table), expected);
        if (type == null)
            return new OllirExprResult(id);

        var isLocalList = table.getLocalVariables(currentMethod).stream().filter(local -> local.getName().equals(id))
                .toList();
        var isParamList = table.getParameters(currentMethod).stream().filter(param -> param.getName().equals(id))
                .toList();
        var isFieldList = table.getFields().stream().filter(field -> field.getName().equals(id)).toList();
        String ollirType = OptUtils.toOllirType(type);

        if (isLocalList.isEmpty() && isParamList.isEmpty() && !isFieldList.isEmpty()) {
            var code = OptUtils.getTemp() + ollirType;
            var computation = new StringBuilder();
            computation.append(code);
            computation.append(SPACE)
                    .append(ASSIGN)
                    .append(ollirType);
            computation.append(SPACE);
            computation.append("getfield");
            computation.append("(this, ");
            computation.append(id);
            computation.append(ollirType);
            computation.append(")");
            computation.append(ollirType);
            computation.append(";\n");
            return new OllirExprResult(code, computation);
        }

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    private OllirExprResult visitFieldAccessExpr(JmmNode node, InferType expected) {
        var lhs = visit(node.getChild(0));
        var lhsType = TypeUtils.getExprType(node.getChild(0), table);
        var fieldName = node.get("field");
        var rhsType = expected;
        if (!lhsType.isArray() && lhsType.getName().equals(table.getClassName())) {
            rhsType = new InferType(table.getFields().stream()
                    .filter(field -> field.getName().equals(fieldName)).findFirst()
                    .orElse(new Symbol(
                            expected != null ? expected.type : null, ""))
                    .getType());
        }
        var ollirType = ".V";
        if (rhsType != null && rhsType.type != null) {
            ollirType = OptUtils.toOllirType(rhsType.type);
        }
        var code = OptUtils.getTemp() + ollirType;
        var computation = new StringBuilder();
        computation.append(lhs.getComputation());
        computation.append(code);
        computation.append(SPACE)
                .append(ASSIGN)
                .append(ollirType);
        computation.append(SPACE);
        if (lhsType.isArray() && fieldName.equals("length")) {
            computation.append("arraylength(");
            computation.append(lhs.getCode());
            computation.append(").i32;\n");
        } else {
            computation.append("getfield");
            computation.append("(");
            computation.append(lhs.getCode());
            if (!lhs.getCode().contains("."))
                computation.append(OptUtils.toOllirType(lhsType));
            computation.append(", ");
            computation.append(fieldName);
            computation.append(ollirType);
            computation.append(")");
            computation.append(ollirType);
            computation.append(";\n");
        }
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayAccessExpr(JmmNode node, InferType expected) {
        var var = visit(node.getChild(0));
        var pos = visit(node.getChild(1));
        var arrayType = TypeUtils.getExprType(node.getChild(0), table);
        var posType = TypeUtils.getExprType(node.getChild(1), table);

        var ollirArrayType = OptUtils.toOllirType(arrayType.getName());
        var ollirPosType  = OptUtils.toOllirType(posType);

        var code = OptUtils.getTemp() + ollirArrayType;
        var computation = new StringBuilder();
        computation.append(var.getComputation());
        computation.append(pos.getComputation());
        computation.append(code);
        computation.append(SPACE)
                .append(ASSIGN)
                .append(ollirArrayType);
        computation.append(SPACE);
        int firstDotIndex = var.getCode().indexOf('.');
        String name = var.getCode().substring(0, firstDotIndex);
        computation.append(name);
        computation.append("[");
        computation.append(pos.getCode());
        computation.append("]");
        computation.append(ollirArrayType);
        computation.append(";\n");

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitFunctionCall(JmmNode node, InferType expected) {

        var type = typeOrExpected(TypeUtils.getExprType(node, table), expected);
        var object = visit(node.getChild(0));
        var functionName = node.get("functionName");
        String callType;

        if (object.getCode().contains(".")) {
            callType = "invokevirtual";
        } else {
            callType = "invokestatic";
        }

        return generateFunction(object.getCode(), callType, object.getComputation(), node, 1, functionName, type,
                expected != null ? expected.needsResult : true);
    }

    private OllirExprResult visitSelfFunctionCall(JmmNode node, InferType expected) {
        var functionName = node.get("functionName");
        var type = typeOrExpected(TypeUtils.getExprType(node, table), expected);
        return generateFunction("this." + table.getClassName(), "invokevirtual", "", node, 0,
                functionName, type, expected != null ? expected.needsResult : true);
    }

    private OllirExprResult generateFunction(
            String object,
            String callType,
            String preComputation,
            JmmNode node,
            int start,
            String functionName,
            Type type,
            boolean needsResult) {

        var computation = new StringBuilder();
        String res;

        var computedArgs = new ArrayList<OllirExprResult>();
        var args = node.getChildren().stream().skip(start).toList();

        if (object.contains(".") && object.split("\\.")[1].equals(table.getClassName()) && table
                .getParameters(functionName) != null) {
            var params = table.getParameters(functionName);
            for (int i = 0; i < params.size(); i++) {
                var computed = visit(args.get(i), new InferType(params.get(i).getType()));
                computedArgs.add(computed);
                computation.append(computed.getComputation());
                computation.append("\n");
            }
        } else {
            for (var arg : args) {
                var computed = visit(arg);
                computedArgs.add(computed);
                computation.append(computed.getComputation());
                computation.append("\n");
            }
        }
        if (type == null || type.getName().equals("void") || !needsResult) {
            res = "";
        } else {
            res = OptUtils.getTemp() + OptUtils.toOllirType(type);
            computation.append(res);
            computation.append(SPACE)
                    .append(ASSIGN)
                    .append(OptUtils.toOllirType(type));
            computation.append(SPACE);
        }
        computation.append(callType);
        computation.append("(");

        computation.append(object);
        computation.append(",\"");
        computation.append(functionName);
        computation.append("\"");
        for (var arg : computedArgs) {
            computation.append(", " + arg.getCode());
        }
        computation.append(")");
        if (type == null) {
            computation.append(".V");
        } else {
            computation.append(OptUtils.toOllirType(type));
        }
        computation.append(";\n");
        return new OllirExprResult(res, preComputation + computation.toString());
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param expected
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, InferType expected) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

    private Type typeOrExpected(Type type, InferType expected) {
        if (type != null)
            return type;
        if (expected == null)
            return null;
        return expected.type;
    }

}
