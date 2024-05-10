package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.Iterator;
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
    ClassUnit classUnit;

    private final FunctionClassMap<TreeNode, String> generators;
    private boolean needsResult;

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
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(GotoInstruction.class, this::generateGoto);

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

        this.classUnit = classUnit;
        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        var superName = "java/lang/Object";
        if (classUnit.getSuperClass() != null)
            superName = handleImports(classUnit.getSuperClass());
        code.append(".super ");
        code.append(superName);
        code.append(NL);

        for (var field : classUnit.getFields()) {
            code.append(".field private ");
            code.append(field.getFieldName());
            code.append(" ");
            code.append(typeJasmin(field.getFieldType()));
            code.append(NL);
        }
        // generate a single constructor method
        code.append("""
                ;default constructor
                .method public <init>()V
                    aload_0
                """);
        code.append("    invokespecial ");
        code.append(superName);
        code.append("/<init>()V\n");
        code.append("""
                    return
                .end method
                """);

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
            // System.out.println("inst - " + inst);
            code.append(";inst - " + inst).append(NL);
            needsResult = !inst.getInstType().equals(InstructionType.CALL);
            for (var label : method.getLabels(inst)) {
                code.append(label);
                code.append(":\n");
            }
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            // System.out.println("instcode - " + instCode);
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

        // System.out.println("hello - " + assign.getRhs());
        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var jasminType = typeJasmin(operand.getType());
        if (operand instanceof ArrayOperand) {
            var arrayOperand = (ArrayOperand) operand;
            code.append("aload ").append(reg).append(NL);
            code.append(generators.apply(arrayOperand.getIndexOperands().get(0)));
            code.append(generators.apply(assign.getRhs()));
            if (jasminType.startsWith("L") || jasminType.startsWith("["))
                code.append("aastore").append(NL);
            else
                code.append("iastore").append(NL);
        } else {
            code.append(generators.apply(assign.getRhs()));
            if (jasminType.startsWith("L") || jasminType.startsWith("["))
                code.append("astore ").append(reg).append(NL);
            else
                code.append("istore ").append(reg).append(NL);
        }
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
        if (operand.getName().equals("this")) {
            return "aload 0\n";
        }
        if (operand.getName().equals("true")) {
            return "ldc 1\n";
        }
        if (operand.getName().equals("false")) {
            return "ldc 0\n";
        }
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var jasminType = typeJasmin(operand.getType());
        if (operand instanceof ArrayOperand) {
            var code = new StringBuilder();
            var arrayOperand = (ArrayOperand) operand;
            code.append("aload ").append(reg).append(NL);
            code.append(generators.apply(arrayOperand.getIndexOperands().get(0)));
            if (jasminType.startsWith("L") || jasminType.startsWith("["))
                return code.toString() + "aaload" + NL;
            return code.toString() + "iaload" + NL;
        }
        if (jasminType.startsWith("L") || jasminType.startsWith("["))
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

    private String generateUnaryOp(UnaryOpInstruction unaryOpInst) {
        var code = new StringBuilder();
        code.append(generators.apply(unaryOpInst.getOperand()));
        code.append("iconst_1").append(NL);
        code.append("ixor").append(NL);
        return code.toString();
    }

    private String generateCall(CallInstruction callInst) {
        var code = new StringBuilder();
        switch (callInst.getInvocationType()) {
            case NEW:
                var operads = callInst.getOperands();
                if (operads.size() > 1) {
                    code.append(generators.apply(callInst.getOperands().get(1)));
                    code.append("newarray int");
                    // var type = ((ArrayType) callInst.getReturnType()).getElementType();
                    // code.append(typeJasmin(type));
                } else {
                    code.append("new ");
                    code.append(handleImports(callInst.getCaller().getType()));
                }
                break;
            case arraylength: {
                var operand = (Operand) callInst.getOperands().get(0);
                code.append(generators.apply(operand));
                code.append("arraylength");
                break;
            }
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
        boolean savedNeedsResult = needsResult;
        needsResult = true;
        var operand = (Operand) callInst.getOperands().get(0);
        var className = handleImports(((Operand) callInst.getOperands().get(0)).getType());
        var methodName = ((LiteralElement) callInst.getOperands().get(1)).getLiteral();
        if (methodName.charAt(0) == '"') {
            methodName = methodName.substring(1, methodName.length() - 1);
        }
        if (!callType.equals("invokestatic")) {
            code.append(generateOperand(operand));
        } else {
            className = handleImports(operand.getName());
            // className = operand.getName();
        }
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
        var jasminType = this.typeJasmin(callInst.getReturnType());
        code.append(jasminType);
        if (!savedNeedsResult && !jasminType.equals("V")) {
            code.append("\npop");
        }
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();
        // TODO: Hardcoded to int return type, needs to be expanded
        if (returnInst.getReturnType().getTypeOfElement().equals(ElementType.VOID)) {
            code.append("return").append(NL);
            return code.toString();
        }
        code.append(generators.apply(returnInst.getOperand()));
        var jasminType = typeJasmin(returnInst.getReturnType());
        if (jasminType.startsWith("L") || jasminType.startsWith("[")) {
            code.append("areturn").append(NL);
            return code.toString();
        }
        code.append("ireturn").append(NL);
        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getFieldInst) {
        var code = new StringBuilder();
        code.append("aload 0 ; push this\n");
        code.append("getfield ");
        code.append(classUnit.getClassName());
        code.append("/");
        code.append(getFieldInst.getField().getName());
        code.append(" ");
        code.append(typeJasmin(getFieldInst.getFieldType()));
        code.append("\n");
        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putFieldInst) {
        var code = new StringBuilder();
        code.append("aload 0 ; push this").append(NL);
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

    private String generateOpCond(OpCondInstruction opCondInst) {
        var code = new StringBuilder();
        code.append(generators.apply(opCondInst.getOperands().get(0)));
        code.append(generators.apply(opCondInst.getOperands().get(1)));
        code.append("isub").append(NL);
        code.append(switch (opCondInst.getCondition().getOperation().getOpType()) {
            case LTH -> "iflt ";
            case LTE -> "ifle ";
            case GTE -> "ifge ";
            default -> throw new IllegalArgumentException(
                    "Unexpected value: " + opCondInst.getCondition().getOperation().getOpType());
        });
        code.append(opCondInst.getLabel()).append(NL);
        return code.toString();
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCondInst) {
        var code = new StringBuilder();
        code.append(generators.apply(singleOpCondInst.getOperands().get(0)));
        code.append("ifne ").append(singleOpCondInst.getLabel()).append(NL);
        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInst) {
        var code = new StringBuilder();
        code.append("goto ").append(gotoInst.getLabel()).append(NL);
        return code.toString();
    }

    @SuppressWarnings("unused")
    private String typeJasmin(String typeString) {
        return typeJasmin("", typeString);
    }

    private String typeJasmin(String ret, String typeString) {
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
        if (typeString.equals(classUnit.getClassName())) {
            return ret + "L" + typeString + ";";
        }
        if (classUnit.isImportedClass(typeString)) {
            for (var importedClass : classUnit.getImports()) {
                if (importedClass.endsWith("." + typeString) || importedClass.equals(typeString)) {
                    return ret + "L" + importedClass.replace(".", "/") + ";";
                }
            }
        }
        return "";
    }

    private String typeJasmin(Type type) {
        var ret = "";
        var typeString = type.toString();
        if (type instanceof ClassType) {
            typeString = ((ClassType) type).getName();
        }
        if (type.getTypeOfElement().equals(ElementType.ARRAYREF)) {
            ret = "[";
            typeString = typeString.substring(0, typeString.length() - 2);
        }
        return typeJasmin(ret, typeString);
    }

    private String handleImports(Type type) {
        var typeString = ((ClassType) type).getName();
        return handleImports(typeString);
    }

    private String handleImports(String typeString) {
        if (classUnit.isImportedClass(typeString)) {
            for (var importedClass : classUnit.getImports()) {
                if (importedClass.endsWith("." + typeString) || importedClass.equals(typeString)) {
                    return importedClass.replace(".", "/");
                }
            }
        }
        return typeString;
    }

}
