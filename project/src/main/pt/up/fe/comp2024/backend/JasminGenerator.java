package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2024.optimization.OptUtils;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class MaxCounter {
    private int count = 0;
    private int max = 0;

    public void add(int x) {
        count += x;
        if (count > max)
            max = count;
    }

    public void sub(int x) {
        count -= x;
    }

    public int getMax() {
        return max;
    }

    public int getCount() {
        return count;
    }

    public void reset() {
        max = 0;
        count = 0;
    }

}

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

    MaxCounter stackMax;

    private final FunctionClassMap<TreeNode, String> generators;
    private boolean needsResult;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        reports = new ArrayList<>();
        code = null;
        currentMethod = null;
        stackMax = new MaxCounter();
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

        stackMax.reset();

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

        var codeTemp = new StringBuilder();

        for (var inst : method.getInstructions()) {
            needsResult = !inst.getInstType().equals(InstructionType.CALL);
            for (var label : method.getLabels(inst)) {
                codeTemp.append(label);
                codeTemp.append(":\n");
            }
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            // System.out.println("instcode - " + instCode);
            codeTemp.append(instCode);
        }

        code.append(TAB).append(".limit stack ").append(stackMax.getMax()).append(NL);
        var maxVirtualReg = method.getVarTable().values().stream().mapToInt(Descriptor::getVirtualReg).max();
        int number = maxVirtualReg.orElse(0); //method.getVarTable().values().stream().map(Descriptor::getVirtualReg).max(Comparator.naturalOrder()).get()
        code.append(TAB).append(".limit locals ").append(number+1)
                .append(NL);

        code.append(codeTemp);

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

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var jasminType = typeJasmin(operand.getType());
        if (operand instanceof ArrayOperand arrayOperand) {
            code.append(reg < 4 ? "aload_" : "aload ").append(reg).append(NL);
            stackMax.add(1);
            code.append(generators.apply(arrayOperand.getIndexOperands().get(0)));
            code.append(generators.apply(assign.getRhs()));
            stackMax.sub(3);
            if (jasminType.startsWith("L") || jasminType.startsWith("["))
                code.append("aastore").append(NL);
            else
                code.append("iastore").append(NL);
        } else {
            if (assign.getRhs() instanceof BinaryOpInstruction binaryOp) {
                if (binaryOp.getOperation().getOpType().equals(OperationType.ADD)) {
                    if (binaryOp.getLeftOperand() instanceof Operand
                            && binaryOp.getRightOperand() instanceof LiteralElement &&
                            currentMethod.getVarTable().get(((Operand) binaryOp.getLeftOperand()).getName())
                                    .getVirtualReg() == reg) {
                        int value = Integer.parseInt(((LiteralElement) binaryOp.getRightOperand()).getLiteral());
                        if (value >= -128 && value <= 127) {
                            code.append("iinc ");
                            code.append(reg);
                            code.append(" ");
                            code.append(value);
                            code.append(NL);
                            return code.toString();
                        }
                    } else if (binaryOp.getRightOperand() instanceof Operand
                            && binaryOp.getLeftOperand() instanceof LiteralElement &&
                            currentMethod.getVarTable().get(((Operand) binaryOp.getRightOperand()).getName())
                                    .getVirtualReg() == reg) {
                        int value = Integer.parseInt(((LiteralElement) binaryOp.getLeftOperand()).getLiteral());
                        if (value >= -128 && value <= 127) {
                            code.append("iinc ");
                            code.append(reg);
                            code.append(" ");
                            code.append(value);
                            code.append(NL);
                            return code.toString();
                        }
                    }
                }

                if (binaryOp.getOperation().getOpType().equals(OperationType.SUB)) {
                    if (binaryOp.getLeftOperand() instanceof Operand
                            && binaryOp.getRightOperand() instanceof LiteralElement &&
                            currentMethod.getVarTable().get(((Operand) binaryOp.getLeftOperand()).getName())
                                    .getVirtualReg() == reg) {
                        int value = -Integer.parseInt(((LiteralElement) binaryOp.getRightOperand()).getLiteral());
                        if (value >= -128 && value <= 127) {
                            code.append("iinc ");
                            code.append(reg);
                            code.append(" ");
                            code.append(value);
                            code.append(NL);
                            return code.toString();
                        }
                    } else if (binaryOp.getRightOperand() instanceof Operand
                            && binaryOp.getLeftOperand() instanceof LiteralElement &&
                            currentMethod.getVarTable().get(((Operand) binaryOp.getRightOperand()).getName())
                                    .getVirtualReg() == reg) {
                        int value = -Integer.parseInt(((LiteralElement) binaryOp.getLeftOperand()).getLiteral());
                        if (value >= -128 && value <= 127) {
                            code.append("iinc ");
                            code.append(reg);
                            code.append(" ");
                            code.append(value);
                            code.append(NL);
                            return code.toString();
                        }
                    }
                }
            }
            code.append(generators.apply(assign.getRhs()));
            stackMax.sub(1);
            if (jasminType.startsWith("L") || jasminType.startsWith("["))
                code.append(reg < 4 ? "astore_" : "astore ").append(reg).append(NL);
            else
                code.append(reg < 4 ? "istore_" : "istore ").append(reg).append(NL);
        }
        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        stackMax.add(1);
        if (literal.getType().toString().equals("INT32")) {
            int number = Integer.parseInt(literal.getLiteral());
            if (number >= -1 && number <= 5) {
                if (number == -1) {
                    return "iconst_m1" + NL;
                }
                return "iconst_" + literal.getLiteral() + NL;
            }
            if (number <= 127 && number >= -128) {
                return "bipush " + literal.getLiteral() + NL;
            }
            if (number <= 32767 && number >= -32768) {
                return "sipush " + literal.getLiteral() + NL;
            }
        }
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        if (operand.getName().equals("this")) {
            stackMax.add(1);
            return "aload_0\n";
        }
        if (operand.getName().equals("true")) {
            stackMax.add(1);
            return "iconst_1\n";
        }
        if (operand.getName().equals("false")) {
            stackMax.add(1);
            return "iconst_0\n";
        }
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var jasminType = typeJasmin(operand.getType());
        if (operand instanceof ArrayOperand arrayOperand) {
            var code = new StringBuilder();
            stackMax.add(1);
            code.append(reg < 4 ? "aload_" : "aload ").append(reg).append(NL);
            code.append(generators.apply(arrayOperand.getIndexOperands().get(0)));
            stackMax.sub(2);
            stackMax.add(1);
            if (jasminType.startsWith("L") || jasminType.startsWith("["))
                return code + "aaload" + NL;
            return code + "iaload" + NL;
        }
        stackMax.add(1);
        if (jasminType.startsWith("L") || jasminType.startsWith("["))
            return (reg < 4 ? "aload_" : "aload ") + reg + NL;
        return (reg < 4 ? "iload_" : "iload ") + reg + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // System.out.println(binaryOp.toTree());

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        if (binaryOp.getOperation().getOpType().toString().equals("LTH")) {
            String temp = OptUtils.getNextTempLabel();
            String temp1 = OptUtils.getNextTempLabel();

            // ver 0 e 1 aqui !!!!!
            code.append("isub").append(NL);
            code.append("iflt ").append(temp).append(NL);
            code.append("ldc 0").append(NL);
            code.append("goto ").append(temp1).append(NL);

            code.append(temp).append(":").append(NL);
            code.append("ldc 1").append(NL);
            code.append("goto ").append(temp1).append(NL);

            code.append(temp1).append(":").append(NL);

            return code.toString();
        }
        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv";
            case SUB -> "isub";
            default -> throw new IllegalArgumentException("Unexpected value: " + binaryOp.getOperation().getOpType());
        };
        stackMax.sub(2);
        stackMax.add(1);
        code.append(op).append(NL);

        return code.toString();
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOpInst) {
        var code = new StringBuilder();
        code.append(generators.apply(unaryOpInst.getOperand()));
        stackMax.add(1);
        code.append("iconst_1").append(NL);
        code.append("ixor").append(NL);
        stackMax.sub(1);
        return code.toString();
    }

    private String generateCall(CallInstruction callInst) {
        var code = new StringBuilder();
        switch (callInst.getInvocationType()) {
            case NEW:
                var operands = callInst.getOperands();
                if (operands.size() > 1) {
                    code.append(generators.apply(callInst.getOperands().get(1)));
                    stackMax.add(1);
                    code.append("newarray int");
                } else {
                    code.append("new ");
                    stackMax.add(1);
                    code.append(handleImports(callInst.getCaller().getType()));
                }
                break;
            case arraylength: {
                var operand = (Operand) callInst.getOperands().get(0);
                code.append(generators.apply(operand));
                stackMax.sub(1);
                stackMax.add(1);
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
        var className = handleImports(callInst.getOperands().get(0).getType());
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
        stackMax.sub(callInst.getArguments().size());
        if (!callType.equals("invokestatic")) {
            stackMax.sub(1);
        }
        var jasminType = this.typeJasmin(callInst.getReturnType());
        code.append(jasminType);
        if (!jasminType.equals("V")) {
            stackMax.add(1);
        }
        if (!savedNeedsResult && !jasminType.equals("V")) {
            stackMax.sub(1);
            code.append("\npop");
        }
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();
        if (returnInst.getReturnType().getTypeOfElement().equals(ElementType.VOID)) {
            code.append("return").append(NL);
            return code.toString();
        }
        code.append(generators.apply(returnInst.getOperand()));
        var jasminType = typeJasmin(returnInst.getReturnType());
        stackMax.sub(1);
        if (jasminType.startsWith("L") || jasminType.startsWith("[")) {
            code.append("areturn").append(NL);
            return code.toString();
        }
        code.append("ireturn").append(NL);
        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getFieldInst) {
        var code = new StringBuilder();
        code.append("aload_0 ; push this\n");
        stackMax.add(1);
        code.append("getfield ");
        code.append(classUnit.getClassName());
        code.append("/");
        code.append(getFieldInst.getField().getName());
        code.append(" ");
        code.append(typeJasmin(getFieldInst.getFieldType()));
        code.append("\n");
        stackMax.sub(1);
        stackMax.add(1);
        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putFieldInst) {
        var code = new StringBuilder();
        code.append("aload_0 ; push this").append(NL);
        stackMax.add(1);
        code.append(generators.apply(putFieldInst.getValue()));
        code.append("putfield ");
        stackMax.sub(2);
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
        stackMax.sub(2);
        stackMax.add(1);
        code.append(switch (opCondInst.getCondition().getOperation().getOpType()) {
            case LTH -> "iflt ";
            case LTE -> "ifle ";
            case GTE -> "ifge ";
            case GTH -> "ifgt ";
            default -> throw new IllegalArgumentException(
                    "Unexpected value: " + opCondInst.getCondition().getOperation().getOpType());
        });
        code.append(opCondInst.getLabel()).append(NL);
        stackMax.sub(1);
        return code.toString();
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCondInst) {
        var code = new StringBuilder();
        code.append(generators.apply(singleOpCondInst.getOperands().get(0)));
        code.append("ifne ").append(singleOpCondInst.getLabel()).append(NL);
        stackMax.sub(1);
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
