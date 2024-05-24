package pt.up.fe.comp2024;

import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp2024.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2024.backend.JasminBackendImpl;
import pt.up.fe.comp2024.optimization.JmmOptimizationImpl;
import pt.up.fe.comp2024.parser.JmmParserImpl;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsSystem;
import java.util.Map;

public class Launcher {

    public static void main(String[] args) {
        SpecsSystem.programStandardInit();

        Map<String, String> config = CompilerConfig.parseArgs(args);

        var inputFile = CompilerConfig.getInputFile(config).orElseThrow();
        if (!inputFile.isFile()) {
            throw new RuntimeException("Option '-i' expects a path to an existing input file, got '" + args[0] + "'.");
        }
        String code = SpecsIo.read(inputFile);

        // Parsing stage
        JmmParserImpl parser = new JmmParserImpl();
        JmmParserResult parserResult = parser.parse(code, config);
        TestUtils.noErrors(parserResult.getReports());

        // Print AST
        System.out.println(parserResult.getRootNode().toTree());

        // Semantic Analysis stage
        JmmAnalysisImpl sema = new JmmAnalysisImpl();
        JmmSemanticsResult semanticsResult = sema.semanticAnalysis(parserResult);
        for (var report : semanticsResult.getReports()) {
            System.out.println(report);
        }
        TestUtils.noErrors(semanticsResult.getReports());

        // Optimization stage
        JmmOptimizationImpl ollirGen = new JmmOptimizationImpl();
        semanticsResult = ollirGen.optimize(semanticsResult);
        System.out.println("====AST OPTIMIZED=============");
        System.out.println(semanticsResult.getRootNode().toTree());
        System.out.println("====END=======================");
        // System.exit(0);

        OllirResult ollirResult = ollirGen.toOllir(semanticsResult);
        TestUtils.noErrors(ollirResult.getReports());
        ollirResult = ollirGen.optimize(ollirResult);

        System.out.println("====OLIR======================");
        // Print OLLIR code
        System.out.println(ollirResult.getOllirCode());
        TestUtils.noErrors(ollirResult.getReports());
        System.out.println("====END=======================");


        // Code generation stage
        JasminBackendImpl jasminGen = new JasminBackendImpl();
        JasminResult jasminResult = jasminGen.toJasmin(ollirResult);
        TestUtils.noErrors(jasminResult.getReports());

        System.out.println("====JASMIN====================");
        // Print Jasmin code
        System.out.println(jasminResult.getJasminCode());
        System.out.println("====END=======================");

        var runOutput = jasminResult.runWithFullOutput();
        System.out.println("\n Result: " + runOutput.getOutput());
        System.out.println("\n Exit code: " + runOutput.getReturnValue());
    }

}
