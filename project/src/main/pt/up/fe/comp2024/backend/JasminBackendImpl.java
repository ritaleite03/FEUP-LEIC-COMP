package pt.up.fe.comp2024.backend;

import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;

public class JasminBackendImpl implements JasminBackend {

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        var jasminGenerator = new JasminGenerator(ollirResult);
        var jasminCode = jasminGenerator.build();
        System.out.println("--- OLLIR ---");
        System.out.println(ollirResult.getOllirCode());
        var temp = new JasminResult(ollirResult, jasminCode, jasminGenerator.getReports());
        System.out.println("--- JASMIN ---");
        System.out.println(temp.getJasminCode());
        return temp;
    }

}
