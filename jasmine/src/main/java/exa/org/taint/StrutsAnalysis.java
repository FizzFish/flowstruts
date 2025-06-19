package exa.org.taint;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import soot.G;
import soot.Scene;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;

/**
 * Analysis entry point for Struts applications using the jasmine framework.
 */
public class StrutsAnalysis {
    public static String analysisAlgorithm = "jasmine";
    private String version = "struts";
    private static StrutsAnalysis sa = null;
    public StrutsAnalysis() {}
    public String getProcessDir() {
        return String.format("../benchmark/%s", version);
    }
    public String getLibPath() {
        return "../benchmark/rt.jar";
    }
    public String getBenchmark() {
        return version;
    }
    public static StrutsAnalysis v() {
        if (sa == null) {
            sa = new StrutsAnalysis();
        }
        return sa;
    }
    public void analysis() throws IOException {
        StrutsSetupApplication application = new StrutsSetupApplication();

        // Create taint wrapper
        File taintWrapperFile = new File("conf/EasyTaintWrapperSource.txt");
        application.setTaintWrapper(new EasyTaintWrapper(taintWrapperFile));

        // Run the analysis
        application.runInfoflow( "conf/SourcesAndSinks-struts.txt");
    }

    public static void main(String[] args) throws IOException {
        StrutsAnalysis sa = v();
        sa.version = "struts-005";
        sa.analysis();
    }
}
