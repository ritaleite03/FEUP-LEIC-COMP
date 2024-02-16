package pt.up.fe.comp.initial;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsIo;

import java.io.File;
import java.util.Collections;

public class JasminTest {

    @Test
    public void ollirToJasminBasic() {
        testOllirToJasmin("pt/up/fe/comp/initial/jasmin/OllirToJasminBasic.ollir");
    }

    @Test
    public void ollirToJasminArithmetics() {
        testOllirToJasmin("pt/up/fe/comp/initial/jasmin/OllirToJasminArithmetics.ollir");
    }


    public static void testOllirToJasmin(String resource, String expectedOutput) {
        // If AstToJasmin pipeline, do not execute test
        if (TestUtils.hasAstToJasminClass()) {
            return;
        }

        var ollirResult = new OllirResult(SpecsIo.getResource(resource), Collections.emptyMap());

        var result = TestUtils.backend(ollirResult);

        var testName = new File(resource).getName();
        System.out.println(testName + ":\n" + result.getJasminCode());
        result.compile();
    }

    public static void testOllirToJasmin(String resource) {
        testOllirToJasmin(resource, null);
    }
}
