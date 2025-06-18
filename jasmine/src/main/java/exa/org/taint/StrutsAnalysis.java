package exa.org.taint;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;

/**
 * Analysis entry point for Struts applications using the jasmine framework.
 */
public class StrutsAnalysis {
    public static String benchmark = "struts";
    public static String analysisAlgorithm = "jasmine";


    public void analysis() throws IOException {
        StrutsSetupApplication application = new StrutsSetupApplication();

        // Create taint wrapper
        File taintWrapperFile = new File("EasyTaintWrapperSource.txt");
        application.setTaintWrapper(new EasyTaintWrapper(taintWrapperFile));

        // Run the analysis
        application.runInfoflow( "SourcesAndSinks-struts.txt");
    }

    public static void main(String[] args) throws IOException {
        new StrutsAnalysis().analysis();
    }
}
