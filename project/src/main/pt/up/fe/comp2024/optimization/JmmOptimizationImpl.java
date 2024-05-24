package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.CompilerConfig;

import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.optimization.GraphColoring;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JmmOptimizationImpl implements JmmOptimization {
    private boolean change;
    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        if (!CompilerConfig.getOptimize(semanticsResult.getConfig())) {
            return semanticsResult;
        }

        ASTOptimization optimizer = new ASTOptimization(semanticsResult.getRootNode(),
                semanticsResult.getSymbolTable());
        optimizer.optimize();
        return new JmmSemanticsResult(optimizer.rootNode, optimizer.table, semanticsResult.getReports(),
                semanticsResult.getConfig());
    }

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());
        var ollirCode = visitor.visit(semanticsResult.getRootNode());
        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        if (CompilerConfig.getRegisterAllocation(ollirResult.getConfig()) == -1) {
            return ollirResult;
        }

        int n = CompilerConfig.getRegisterAllocation(ollirResult.getConfig());
        ClassUnit classUnit = ollirResult.getOllirClass();
        classUnit.buildCFGs();

        for(Method method : classUnit.getMethods()) {
            method.buildVarTable();
            int size = method.getInstructions().size() + 1;
            Node begin = method.getBeginNode();

            //System.out.println("\n \n Test \n");
            //method.show();
            //System.out.println("\n \n End \n");

            Set<Integer> visit = new HashSet<>();
            List<Set<String>> use = new ArrayList<>();
            List<Set<String>> def = new ArrayList<>();
            List<Set<String>> liveIn = new ArrayList<>();
            List<Set<String>> liveOut = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                use.add(new HashSet<>());
                def.add(new HashSet<>());
                liveIn.add(new HashSet<>());
                liveOut.add(new HashSet<>());
            }
            dfs(begin, visit, use, def);

            change = true;
            while (change) {
                change = false;
                visit.clear();
                computeLiveSet(begin, visit, liveIn, liveOut, use, def);
            }
            List<Set<String>> defJoinLiveOut = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                Set<String> join = Stream.concat(def.get(i).stream(), liveOut.get(i).stream()).collect(Collectors.toSet());
                defJoinLiveOut.add(join);
            }
            Set<String> vars = new HashSet<>();
            for (var s : defJoinLiveOut) {
                vars.addAll(s);
            }
            List<String> params = method.getParams().stream().map(param -> ((Operand) param).getName()).toList();
            params.forEach(vars::remove);
            GraphColoring graphColoring = new GraphColoring();
            for (String var : vars) {
                graphColoring.addNode(var);
            }
            for (Set<String> set : defJoinLiveOut) {
                for (String n1 : set) {
                    for (String n2 : set) {
                        if (!n1.equals(n2)) {
                            graphColoring.addEdge(n1, n2);
                        }
                    }
                }
            }

            for (String var : vars) {
                graphColoring.addColor(var);
            }

            if(n == 0) {

                for (var entry : graphColoring.getGraphColors().entrySet()) {
                    method.getVarTable().get(entry.getKey()).setVirtualReg(entry.getValue() + method.getParams().size() + (method.isStaticMethod() ? 0 : 1));
                }

            }
            else if (n >= 1){
                int minRegisterNumber = method.getParams().size() + (method.isStaticMethod() ? 0 : 1);
                for (var entry : graphColoring.getGraphColors().entrySet()) {
                    int register = entry.getValue() + method.getParams().size() + (method.isStaticMethod() ? 0 : 1);
                    method.getVarTable().get(entry.getKey()).setVirtualReg(register);
                    if(register > minRegisterNumber){
                        minRegisterNumber = register + 1;
                    }
                }
                String message =  "Minimum number of local variables require for the method " + method.getMethodName() + " is "+minRegisterNumber + ", available " + n;

                if(minRegisterNumber > n){
                    System.out.println("\n \n" + message + "\n \n");
                    Report report = Report.newError(
                            Stage.OPTIMIZATION,
                            -1,
                            -1,
                            message,
                            null);
                    ollirResult.getReports().add(report);
                    return ollirResult;
                }
            }

            HashMap<String, Descriptor> varTable = method.getVarTable();
            StringBuilder message = new StringBuilder();

            var var1 = varTable.entrySet().iterator();

            message.append("Method name:");
            message.append(method.getMethodName());
            message.append("\n");

            while(var1.hasNext()) {
                Map.Entry<String, Descriptor> entry = var1.next();
                String key = (String) entry.getKey();
                Descriptor d1 = (Descriptor) entry.getValue();
                //System.out.println("\t\t" + method.getMethodName() + ": Var name: " + key + " scope: " + String.valueOf(d1.getScope()) + " virtual register: " + d1.getVirtualReg());
                message.append("Var name: ");
                message.append(key);
                message.append(" scope: ");
                message.append(String.valueOf(d1.getScope()));
                message.append(" virtual register: ");
                message.append(d1.getVirtualReg());
                message.append("\n");
            }

            message.append("\n");

            ollirResult.getReports().add(Report.newLog(
                    Stage.OPTIMIZATION,
                    -1,
                    -1,
                    message.toString(),
                    null));

        }

        return ollirResult;
    }

    private void dfs(Node node, Set<Integer> visit, List<Set<String>> use, List<Set<String>> def){
        if(node.getNodeType() == NodeType.BEGIN){
            if(node.getSucc1().getNodeType() != NodeType.END) {
                dfs(node.getSucc1(), visit, use, def);
            }
            return;
        }
        Instruction inst = (Instruction) node;

        switch (inst.getInstType()) {
            case BRANCH: {
                CondBranchInstruction branch = (CondBranchInstruction) inst;
                List<Element> a = branch.getOperands();
                for (Element e : a) {
                    if(!e.isLiteral()){
                        use.get(node.getId()).add(((Operand) e).getName());
                    }
                }
                break;
            }
            case ASSIGN:{
                AssignInstruction assign = (AssignInstruction) inst;
                Operand dest = (Operand) assign.getDest();
                if(dest instanceof ArrayOperand arrayOperand){
                    use.get(node.getId()).add(dest.getName());
                    for(Element e : arrayOperand.getIndexOperands()){
                        if(!e.isLiteral()){
                            use.get(node.getId()).add(((Operand) e).getName());
                        }
                    }
                }
                else {
                    def.get(node.getId()).add(dest.getName());
                }
                Instruction rhs = assign.getRhs();
                List<String> rhsOpe = getInstructionOperands(rhs);
                use.get(node.getId()).addAll(rhsOpe);
                break;
            }
            case RETURN:{
                ReturnInstruction ret = (ReturnInstruction) inst;
                Element element = ret.getOperand();
                if(element != null && !element.isLiteral()){
                    use.get(node.getId()).add(((Operand) element).getName());
                }
                break;
            }
        }
        visit.add(node.getId());
        for(Node successor : node.getSuccessors()){
            if(!visit.contains(successor.getId()) && successor.getNodeType() != NodeType.END) {
                dfs(successor, visit, use, def);
            }
        }
    }

    private List<String> getInstructionOperands(Instruction inst){
        List<String> elements = new ArrayList<>();
        switch (inst.getInstType()) {
            case UNARYOPER:{
                UnaryOpInstruction unary = (UnaryOpInstruction) inst;
                Element element = unary.getOperand();
                if(!element.isLiteral()){
                    elements.add(((Operand) element).getName());
                }
                break;
            }
            case BINARYOPER: {
                BinaryOpInstruction binary = (BinaryOpInstruction) inst;
                Element e1 = binary.getLeftOperand();
                if (!e1.isLiteral()) {
                    elements.add(((Operand) e1).getName());
                }
                Element e2 = binary.getRightOperand();
                if (!e2.isLiteral()) {
                    elements.add(((Operand) e2).getName());
                }
                break;
            }
            case NOPER:{
                SingleOpInstruction single = (SingleOpInstruction)inst;
                Element element = single.getSingleOperand();
                if(!element.isLiteral()){
                    elements.add(((Operand) element).getName());
                }
                break;
            }
            case CALL:{
                CallInstruction call = (CallInstruction) inst;
                List<Element> arguments = call.getArguments();
                for(Element e : arguments){
                    if(!e.isLiteral()){
                        elements.add(((Operand) e).getName());
                    }
                }
            }
        }
        return elements;
    }

    private void computeLiveSet(Node node, Set<Integer> visit, List<Set<String>> liveIn, List<Set<String>> liveOut, List<Set<String>> use, List<Set<String>> def){
        if(node.getNodeType() == NodeType.BEGIN){
            if(node.getSucc1().getNodeType() != NodeType.END) {
                computeLiveSet(node.getSucc1(), visit, liveIn, liveOut, use, def);
            }
            return;
        }
        int nodeId =  node.getId();
        Set<String> oldIn = new HashSet<>(liveIn.get(nodeId));
        Set<String> oldOut = new HashSet<>(liveOut.get(nodeId));

        Set<String> newIn = oldOut.stream().filter(x->!def.get(nodeId).contains(x)).collect(Collectors.toSet());
        newIn.addAll(use.get(nodeId));
        Set<String> newOut = new HashSet<>();

        visit.add(node.getId());
        for(Node successor : node.getSuccessors()){
            if(successor.getNodeType() != NodeType.END) {
                int successorId = successor.getId();
                newOut.addAll(liveIn.get(successorId));
            }
        }
        if (!newIn.equals(oldIn)) {
            change = true;
            liveIn.set(nodeId, newIn);
        }
        if (!newOut.equals(oldOut)) {
            change = true;
            liveOut.set(nodeId, newOut);
        }
        for(Node successor : node.getSuccessors()){
            if(!visit.contains(successor.getId()) && successor.getNodeType() != NodeType.END) {
                computeLiveSet(successor, visit, liveIn, liveOut, use, def);
            }
        }
    }
}
