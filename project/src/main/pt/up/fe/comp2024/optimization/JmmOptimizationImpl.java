package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2024.CompilerConfig;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

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
        var temp = new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
        System.out.println("------------------ OLLIR ------------------");
        System.out.println(temp.getOllirCode());
        System.out.println("------------------ OLLIR ------------------");
        return temp;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        // TODO: Do your OLLIR-based optimizations here

        return ollirResult;
    }
}
