package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Type, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit("FuncExpr", this::visitFunctionCall);
        addVisit("SelfFuncExpr", this::visitSelfFunctionCall);
        addVisit("NewExpr", this::visitNewExpr);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitInteger(JmmNode node, Type expected) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitNewExpr(JmmNode node, Type expected) {
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

    private OllirExprResult visitBinExpr(JmmNode node, Type expected) {

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
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

    private OllirExprResult visitVarRef(JmmNode node, Type expected) {

        var id = node.get("name");
        Type type = typeOrExpected(TypeUtils.getExprType(node, table), expected);
        if (type == null)
            return new OllirExprResult(id);

        String ollirType = OptUtils.toOllirType(type);

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    private OllirExprResult visitFunctionCall(JmmNode node, Type expected) {

        var type = typeOrExpected(TypeUtils.getExprType(node, table), expected);
        var object = visit(node.getChild(0));
        var functionName = node.get("functionName");
        String callType;

        if (object.getCode().contains(".")) {
            callType = "invokevirtual";
        } else {
            callType = "invokestatic";
        }

        if(node.getKind().toString().equals("FuncExpr")){
            var fieldName = node.getChild(0).get("name");
            var isFieldList = table.getFields().stream().filter(field->field.getName().equals(fieldName)).toList();

            if(!isFieldList.isEmpty()){
                return generateFunction(object.getCode(), callType, object.getComputation(), node, 1, functionName, type,fieldName,true);
            }

        }

        return generateFunction(object.getCode(), callType, object.getComputation(), node, 1, functionName, type,"",false);
    }

    private OllirExprResult visitSelfFunctionCall(JmmNode node, Type expected) {
        System.out.println("oiii");
        var functionName = node.get("functionName");
        var type = typeOrExpected(TypeUtils.getExprType(node, table), expected);
        return generateFunction("this." + table.getClassName(), "invokevirtual", "", node, 0,
                functionName, type,"",false);
    }

    private OllirExprResult generateFunction(
            String object,
            String callType,
            String preComputation,
            JmmNode node,
            int start,
            String functionName,
            Type type,
            String fieldName,
            Boolean getfield) {

        var computation = new StringBuilder();
        String res;

        if(getfield){
            var fieldType = table.getFields().stream().filter(field->field.getName().equals(fieldName)).toList().get(0).getType();
            res = OptUtils.getTemp() + OptUtils.toOllirType(fieldType);
            computation.append(res);
            computation.append(SPACE)
                    .append(ASSIGN)
                    .append(OptUtils.toOllirType(fieldType));
            computation.append(SPACE);
            computation.append("getfield");
            computation.append("(this, ");
            computation.append(fieldName);
            computation.append("."+fieldType.getName());
            computation.append(")."+fieldType.getName());
            computation.append(";\n");

            object = res;
        }
        if (type == null) {
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
        for (int i = start; i < node.getChildren().size(); i++) {
           var arg = visit(node.getJmmChild(i));
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
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Type expected) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

    private Type typeOrExpected(Type type, Type expected) {
        if (type != null)
            return type;
        return expected;
    }

}
