package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(PutFieldInstruction.class, this::generatePutField);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }

    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        // TODO: Hardcoded to Object, needs to be expanded
        code.append(".super java/lang/Object").append(NL);
        for (var field : classUnit.getFields()) {
            code.append(".field private ");
            code.append(field.getFieldName());
            code.append(" ");
            code.append(typeJasmin(field.getFieldType()));
            code.append(NL);
        }
        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial java/lang/Object/<init>()V
                    return
                .end method
                """;
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {
        // set method
        currentMethod = method;
        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT
                ? method.getMethodAccessModifier().name().toLowerCase() + " "
                : "";

        var isStatic = method.isStaticMethod() ? "static " : "";
        var methodName = method.getMethodName();
        code.append("\n.method ").append(modifier).append(isStatic).append(methodName).append("(");

        // Add params
        for (int i = 0; i < method.getParams().size(); i++) {
            code.append(this.typeJasmin(method.getParams().get(i).getType()));
        }

        // Add return
        var returnType = method.getReturnType();
        code.append(")").append(this.typeJasmin(returnType)).append(NL);

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {

            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        // TODO: Hardcoded for int type, needs to be expanded
        if (operand.getType() instanceof ClassType)
            code.append("astore ").append(reg).append(NL);
        else
            code.append("istore ").append(reg).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        // System.out.println("Getting " + operand.getName());
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        if (operand.getType() instanceof ClassType)
            return "aload " + reg + NL;
        return "iload " + reg + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv";
            case SUB -> "isub";
            default -> throw new IllegalArgumentException("Unexpected value: " + binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateCall(CallInstruction callInst) {
        var code = new StringBuilder();
        switch (callInst.getInvocationType()) {
            case NEW:
                code.append("new ");
                code.append(((ClassType) callInst.getCaller().getType()).getName());
                break;
            case arraylength:
                break;
            case invokeinterface:
                invokeMethod(code, callInst, "invokeinterface");
                break;
            case invokespecial:
                invokeMethod(code, callInst, "invokespecial");
                break;
            case invokestatic:
                invokeMethod(code, callInst, "invokestatic");
                break;
            case invokevirtual:
                invokeMethod(code, callInst, "invokevirtual");
                break;
            case ldc:
                break;
            default:
                break;

        }
        code.append("\n");
        return code.toString();
    }

    private void invokeMethod(StringBuilder code, CallInstruction callInst, String callType) {
        System.out.println(callInst.toTree());
        var operand = (Operand) callInst.getOperands().get(0);
        var className = ((ClassType) ((Operand) callInst.getOperands().get(0)).getType()).getName();
        var methodName = ((LiteralElement) callInst.getOperands().get(1)).getLiteral();
        if (methodName.charAt(0) == '"') {
            methodName = methodName.substring(1, methodName.length() - 1);
        }
        if (!callType.equals("invokestatic"))
            code.append(generateOperand(operand));
        else
            className = operand.getName();
        for (var param : callInst.getOperands().stream().skip(2).toList()) {
            code.append(generators.apply(param));
        }

        code.append(callType);
        code.append(" ");
        code.append(className);
        code.append("/");
        code.append(methodName);
        code.append("(");
        for (int i = 0; i < callInst.getArguments().size(); i++) {
            code.append(this.typeJasmin(callInst.getArguments().get(i).getType()));
        }
        code.append(")");
        code.append(this.typeJasmin(callInst.getReturnType()));
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();
        // TODO: Hardcoded to int return type, needs to be expanded
        if (returnInst.getReturnType().getTypeOfElement().equals(ElementType.VOID)) {
            code.append("return").append(NL);
            return code.toString();
        }
        code.append(generators.apply(returnInst.getOperand()));
        code.append("ireturn").append(NL);
        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getFieldInst) {
        var code = new StringBuilder();
        code.append("aload 0 ; push this\n");
        code.append("getfield ");
        code.append(ollirResult.getOllirClass().getClassName());
        code.append("/");
        code.append(getFieldInst.getField().getName());
        code.append(" ");
        code.append(typeJasmin(getFieldInst.getFieldType()));
        code.append("\n");
        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putFieldInst) {
        var code = new StringBuilder();
        code.append("aload 0 ; push this\n");
        code.append(generators.apply(putFieldInst.getValue()));
        code.append("putfield ");
        code.append(ollirResult.getOllirClass().getClassName());
        code.append("/");
        code.append(putFieldInst.getField().getName());
        code.append(" ");
        code.append(typeJasmin(putFieldInst.getValue().getType()));
        code.append("\n");
        return code.toString();
    }

    private String typeJasmin(Type type) {
        var ret = "";
        var typeString = type.toString();
        if (type.getTypeOfElement().equals(ElementType.ARRAYREF)) {
            ret = "[";
            typeString = typeString.substring(0, typeString.length() - 2);
        }
        switch (typeString) {
            case "INT32":
                return ret + "I";
            case "BOOLEAN":
                return ret + "Z";
            case "STRING":
                return ret + "Ljava/lang/String;";
            case "VOID":
                return ret + "V";
        }
        return "";
    }

}
