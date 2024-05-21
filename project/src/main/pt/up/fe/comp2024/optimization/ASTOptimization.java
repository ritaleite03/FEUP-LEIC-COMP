package pt.up.fe.comp2024.optimization;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2024.ast.Kind;

class CFGNode {
    BitSet gen = new BitSet();
    BitSet kill = new BitSet();
    BitSet in;
    BitSet out;
    List<JmmNode> stmts = new ArrayList<>();
    List<CFGNode> children = new ArrayList<>();
    int id;

    public CFGNode(int id) {
        this.id = id;
    }
}

class AST_CFG {
    private final SymbolTable table;
    private final String currentMethod;
    private Map<String, JmmNode> constants = new HashMap<>();
    private CFGNode entry;
    private List<CFGNode> all = new ArrayList<>();
    private List<JmmNode> gens = new ArrayList<>();
    private BitSet isConstant = new BitSet();

    public CFGNode creatNode() {
        var node = new CFGNode(all.size());
        all.add(node);
        return node;
    }

    public AST_CFG(SymbolTable table, String currentMethod) {
        this.table = table;
        this.currentMethod = currentMethod;
    }

    private void computeKillGenSets() {
        for (var node : all) {
            node.in = new BitSet(gens.size());
            node.in.flip(0, gens.size());
            node.out = new BitSet(gens.size());
            node.out.flip(0, gens.size());
            Map<String, Integer> vars = new HashMap<>();
            for (var stmt : node.stmts) {
                if (stmt.getKind().equals("AssignStmt")) {
                    vars.put(stmt.get("name"), stmt.getObject("genIndex", Integer.class));
                    for (var gen : gens) {
                        if (gen.get("name").equals(stmt.get("name"))) {
                            node.kill.set(gen.getObject("genIndex", Integer.class));
                        }
                    }
                }
            }
            for (var index : vars.values()) {
                node.gen.set(index);
            }
        }
        entry.in = new BitSet(gens.size());
    }

    static private BitSet or(BitSet a, BitSet b) {
        var ca = ((BitSet) a.clone());
        ca.or(b);
        return ca;
    }

    static private BitSet and(BitSet a, BitSet b) {
        var ca = ((BitSet) a.clone());
        ca.and(b);
        return ca;
    }

    static private BitSet andNot(BitSet a, BitSet b) {
        var ca = ((BitSet) a.clone());
        ca.andNot(b);
        return ca;
    }

    private void computeInOutSets() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var node : all) {
                var out = or(node.gen, andNot(node.in, node.kill));
                if (!out.equals(node.out)) {
                    node.out = out;
                    changed = true;
                }
                for (var child : node.children) {
                    var in = and(child.in, out);
                    if (!in.equals(child.in)) {
                        child.in = in;
                    }
                }
            }
        }
    }

    public boolean constProp() {
        boolean changed = false;
        for (var node : all) {
            constants.clear();
            for (int i = 0; i < node.in.length(); i++) {
                if (node.in.get(i) && isConstant.get(i)) {
                    var gen = gens.get(i);
                    constants.put(gen.get("name"), gen.getChild(0));
                }
            }
            for (var stmt : node.stmts) {
                changed |= constPropStatement(stmt);
            }
        }
        return changed;
    }

    public boolean constPropStatement(JmmNode stmt) {
        if (stmt.isInstance("Expr")) {
            return constPropExpr(stmt);
        }
        boolean changed = false;
        switch (stmt.getKind()) {
            case ("VarStmt"):
                changed |= constPropExpr(stmt.getChild(0));
                break;
            case ("AssignStmt"): {
                changed |= constPropExpr(stmt.getChild(0));
                var value = stmt.getChild(0);
                String name = stmt.get("name");
                constants.remove(name);
                var vars = table.getLocalVariables(currentMethod);
                for (var v : vars) {
                    if (v.getName().equals(name)) {
                        var type = v.getType();
                        if (type.isArray())
                            break;
                        switch (type.getName()) {
                            case "int":
                                if (value.getKind().equals("IntegerLiteral")) {
                                    constants.put(name, value);
                                }
                                break;
                            case "boolean":
                                if (value.getKind().equals("VarRefExpr")
                                        && (value.get("name").equals("true") || value.get("name").equals("false"))) {
                                    constants.put(name, value);
                                }
                                break;
                        }
                    }
                }
                break;
            }
            case ("AssignStmtArray"): {
                changed |= constPropExpr(stmt.getChild(0));
                changed |= constPropExpr(stmt.getChild(1));
                break;
            }
            case ("ReturnStmt"): {
                changed |= constPropExpr(stmt.getChild(0));
                break;
            }
        }
        return changed;
    }

    public boolean constPropExpr(JmmNode expr) {
        boolean changed = false;
        switch (expr.getKind()) {
            case "VarRefExpr":
                String name = expr.get("name");
                if (constants.containsKey(name)) {
                    changed = true;
                    var value = constants.get(name);
                    var newNode = value.copyNode();
                    expr.replace(newNode);
                }
                break;
            case "IntegerLiteral", "FieldAccessExpr", "NewExpr":
                break;
            case "ArrayExpr", "FuncExpr", "SelfFuncExpr": {
                for (var exp : expr.getChildren()) {
                    changed |= constPropExpr(exp);
                }
                break;
            }
            case "NewArrayExpr", "UnaryExpr": {
                changed |= constPropExpr(expr.getChild(0));
                break;
            }
            case "BinaryExpr", "ArrayAccessExpr": {
                changed |= constPropExpr(expr.getChild(0));
                changed |= constPropExpr(expr.getChild(1));
                break;
            }
        }
        return changed;
    }

    private String listAttribute(List<JmmNode> nodes, String name) {
        return nodes.stream().map((stmt) -> stmt.getOptional(name).orElse("unkonw")).collect(
                Collectors.joining(","));
    }

    public String dump() {

        String graph = "";
        for (var gen : gens) {
            graph += "//";
            graph += gen.getObject("genIndex", Integer.class);
            graph += " ";
            graph += gen.get("lineStart");
            graph += " ";
            graph += gen.get("name");
            graph += " ";
            graph += isConstant.get(gen.getObject("genIndex", Integer.class));
            graph += "\n";
        }
        graph += "digraph {\n";
        for (var node : all) {
            graph += node.id + "[label=\"";
            graph += listAttribute(node.stmts, "lineStart");
            graph += "\\ngen=";
            graph += node.gen;
            graph += "\\nkill=";
            graph += node.kill;
            graph += "\\nin=";
            graph += node.in;
            graph += "\\nout=";
            graph += node.out;
            graph += "\"]\n";
            for (var child : node.children) {
                graph += node.id + "->" + child.id + "\n";
            }
        }
        graph += "\n}";
        return graph;
    }

    public static AST_CFG build(JmmNode method, SymbolTable table) {
        var cfg = new AST_CFG(table, method.get("name"));
        cfg.entry = cfg.creatNode();
        cfg.build(method.getChildren(), cfg.entry);
        cfg.computeKillGenSets();
        cfg.computeInOutSets();
        return cfg;
    }

    private CFGNode build(List<JmmNode> nodes, CFGNode current) {
        for (var stmt : nodes) {
            current = build(stmt, current);
        }
        return current;
    }

    private CFGNode build(JmmNode stmt, CFGNode current) {
        switch (stmt.getKind()) {
            case "AssignStmt":
                stmt.putObject("genIndex", gens.size());
                var name = stmt.get("name");
                var value = stmt.getChild(0);
                var vars = table.getLocalVariables(currentMethod);
                for (var v : vars) {
                    if (v.getName().equals(name)) {
                        var type = v.getType();
                        if (type.isArray())
                            break;
                        switch (type.getName()) {
                            case "int":
                                if (value.getKind().equals("IntegerLiteral")) {
                                    isConstant.set(gens.size());
                                }
                                break;
                            case "boolean":
                                if (value.getKind().equals("VarRefExpr")
                                        && (value.get("name").equals("true") || value.get("name").equals("false"))) {
                                    isConstant.set(gens.size());
                                }
                                break;
                        }
                    }
                }
                gens.add(stmt);
            case "VarStmt", "AssignStmtArray", "ReturnStmt":
                current.stmts.add(stmt);
                break;
            case "IfStmt":
                current.stmts.add(stmt.getChild(0));
                var ifCFG = creatNode();
                var elseCFG = creatNode();
                System.out.println("//if" + ifCFG.id);
                System.out.println("//else" + elseCFG.id);
                current.children.add(ifCFG);
                current.children.add(elseCFG);

                var endIfCFG = build(stmt.getChild(1), ifCFG);
                var endElseCFG = build(stmt.getChild(2), elseCFG);
                current = creatNode();
                System.out.println("//endif" + current.id);
                endIfCFG.children.add(current);
                endElseCFG.children.add(current);
                break;
            case "WhileStmt":
                var whileCFG = creatNode();
                whileCFG.stmts.add(stmt.getChild(0));
                var whileBodyCFG = creatNode();
                whileCFG.children.add(whileBodyCFG);
                var endWhileCFG = build(stmt.getChild(1), whileBodyCFG);
                current.children.add(whileCFG);
                endWhileCFG.children.add(whileCFG);
                current = creatNode();
                whileCFG.children.add(current);
                break;
            case "MultiStmt":
                current = build(stmt.getChildren(), current);
                break;
        }
        return current;
    }
}

public class ASTOptimization {
    public final JmmNode rootNode;
    public final SymbolTable table;
    private boolean changed = true;

    public ASTOptimization(JmmNode rootNode, SymbolTable symbolTable) {
        this.rootNode = rootNode;
        this.table = symbolTable;
    }

    public void optimize() {
        while (changed) {
            changed = false;
            for (var method : rootNode.getChildren(Kind.CLASS_DECL).get(0).getChildren(Kind.METHOD_DECL)) {
                visitMethod(method);
            }
            System.out.println("changed");
            System.out.println(changed);
        }
    }

    public void visitMethod(JmmNode method) {
        constFoldMethod(method);
        AST_CFG cfg = AST_CFG.build(method, table);
        System.out.println("===================================");
        System.out.println(cfg.dump());
        System.out.println("===================================");
        changed |= cfg.constProp();
    }

    public void constFoldMethod(JmmNode method) {
        for (var stmt : method.getChildren()) {
            constFoldStatement(stmt);
        }
    }

    public void constFoldStatement(JmmNode stmt) {
        switch (stmt.getKind()) {
            case ("MultiStmt"):
                stmt.getChildren().forEach(stm -> constFoldStatement(stm));
                break;
            case ("IfStmt"): {
                constFoldExpr(stmt.getChild(0));
                constFoldStatement(stmt.getChild(1));
                constFoldStatement(stmt.getChild(2));
                break;
            }
            case ("WhileStmt"): {
                constFoldExpr(stmt.getChild(0));
                constFoldStatement(stmt.getChild(1));
                break;
            }
            case ("VarStmt"):
                constFoldExpr(stmt.getChild(0));
                break;
            case ("AssignStmt"):
                constFoldExpr(stmt.getChild(0));
                break;
            case ("AssignStmtArray"): {
                constFoldExpr(stmt.getChild(0));
                constFoldExpr(stmt.getChild(1));
                break;
            }
            case ("ReturnStmt"): {
                constFoldExpr(stmt.getChild(0));
                break;
            }
        }
    }

    public void constFoldExpr(JmmNode expr) {
        switch (expr.getKind()) {
            case "IntegerLiteral", "VarRefExpr", "FieldAccessExpr", "NewExpr":
                break;
            case "ParenExpr": {
                changed = true;
                constFoldExpr(expr.getChild(0));
                expr.replace(expr.getChild(0));
                break;
            }
            case "ArrayExpr", "FuncExpr", "SelfFuncExpr": {
                for (var exp : expr.getChildren()) {
                    constFoldExpr(exp);
                }
                break;
            }
            case "NewArrayExpr": {
                constFoldExpr(expr.getChild(0));
                break;
            }
            case "UnaryExpr": {
                constFoldExpr(expr.getChild(0));
                var child = expr.getChild(0);
                if (child.getKind().equals("VarRefExpr"))
                    if (child.get("name").equals("true")) {
                        changed = true;

                        var newNode = new JmmNodeImpl("VarRefExpr");
                        newNode.put("name", "false");
                        expr.replace(newNode);

                    } else if (child.get("name").equals("false")) {
                        changed = true;

                        var newNode = new JmmNodeImpl("VarRefExpr");
                        newNode.put("name", "true");
                        expr.replace(newNode);

                    }
                break;
            }
            case "BinaryExpr": {
                constFoldExpr(expr.getChild(0));
                constFoldExpr(expr.getChild(1));
                var left = expr.getChild(0);
                var right = expr.getChild(1);

                boolean leftIsInt = left.getKind().equals("IntegerLiteral");
                boolean rightIsInt = right.getKind().equals("IntegerLiteral");
                int leftValue = 0;
                int rightValue = 0;
                if (leftIsInt)
                    leftValue = Integer.parseInt(left.get("value"));
                if (rightIsInt)
                    rightValue = Integer.parseInt(right.get("value"));
                boolean leftIsVar = left.getKind().equals("VarRefExpr");
                String leftName = "";
                if (leftIsVar)
                    leftName = left.get("name");
                boolean rightIsVar = right.getKind().equals("VarRefExpr");
                String rightName = "";
                if (rightIsVar)
                    rightName = right.get("name");
                boolean leftIsBool = leftIsVar && (leftName.equals("true") || leftName.equals("false"));
                boolean rightIsBool = rightIsVar && (rightName.equals("true") || rightName.equals("false"));
                boolean leftBool = false;
                boolean rightBool = false;
                if (leftIsBool)
                    leftBool = leftName.equals("true");
                if (rightIsBool)
                    rightBool = rightName.equals("true");

                switch (expr.get("op")) {
                    case "+":
                        if (leftIsInt && rightIsInt) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "" + (leftValue + rightValue));
                            expr.replace(newNode);
                        } else if (leftIsInt && leftValue == 0) {
                            changed = true;
                            expr.replace(right);
                        } else if (rightIsInt && rightValue == 0) {
                            changed = true;
                            expr.replace(left);
                        }
                        break;
                    case "-":
                        if (leftIsInt && rightIsInt) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "" + (leftValue - rightValue));
                            expr.replace(newNode);
                        } else if (rightIsInt && rightValue == 0) {
                            changed = true;
                            expr.replace(left);
                        } else if (leftIsVar && rightIsVar && leftName.equals(rightName)) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "0");
                            expr.replace(newNode);
                        }
                        break;
                    case "*":
                        if (leftIsInt && rightIsInt) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "" + (leftValue * rightValue));
                            expr.replace(newNode);
                        } else if ((leftIsInt && leftValue == 0) || (rightIsInt && rightValue == 0)) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "0");
                            expr.replace(newNode);
                        } else if (leftIsInt && leftValue == 1) {
                            changed = true;
                            expr.replace(right);
                        } else if (rightIsInt && rightValue == 1) {
                            changed = true;
                            expr.replace(left);
                        }
                        break;
                    case "/":
                        if (leftIsInt && rightIsInt && rightValue != 0) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "" + (leftValue / rightValue));
                            expr.replace(newNode);
                        } else if (leftIsInt && leftValue == 0) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "0");
                            expr.replace(newNode);
                        } else if (rightIsInt && rightValue == 1) {
                            changed = true;
                            expr.replace(left);
                        } else if (leftIsVar && rightIsVar && leftName.equals(rightName)) {
                            changed = true;
                            var newNode = new JmmNodeImpl("IntegerLiteral");
                            newNode.put("value", "1");
                            expr.replace(newNode);
                        }
                        break;
                    case "<":
                        if (leftIsInt && rightIsInt) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", (leftValue < rightValue) ? "true" : "false");
                            expr.replace(newNode);
                        } else if (leftIsVar && rightIsVar && leftName.equals(rightName)) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", "false");
                            expr.replace(newNode);
                        }
                        break;
                    case "&&":
                        if (leftIsBool && rightIsBool) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", (leftBool && rightBool) ? "true" : "false");
                            expr.replace(newNode);
                        } else if (leftName.equals(rightName)) {
                            changed = true;
                            expr.replace(left);
                        } else if (leftIsBool && leftBool) {
                            changed = true;
                            expr.replace(right);
                        } else if (leftIsBool && !leftBool) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", "false");
                            expr.replace(newNode);
                        } else if (rightIsBool && rightBool) {
                            changed = true;
                            expr.replace(left);
                        } else if (rightIsBool && !rightBool) {
                            changed = true;
                            var newNode = new JmmNodeImpl("VarRefExpr");
                            newNode.put("name", "false");
                            expr.replace(newNode);
                        }
                        break;
                }
                break;
            }
            case "ArrayAccessExpr": {
                constFoldExpr(expr.getChild(0));
                constFoldExpr(expr.getChild(1));
            }
        }
    }
}
