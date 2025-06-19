package exa.org.taint;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;

public class SpringAnalysis {
    public static  String SOURCE_FILE_NAME ;
    private static String target;
    public static String analysisAlgorithm = "jasmine";

    public SpringAnalysis(String target) throws IOException {
        this.target = target;

    }
    public void analysis(String config) throws IOException {
        //SetUpApplication implements a common interface that supports all data flow analysis of taint wrappers.
        SpringSetupApplication application = new SpringSetupApplication(target, config);
        //The main part of the program
        application.runInfoflow();
    }
    public static void main(String []args) throws IOException {
        String target = "demo";
        String config = String.format("src/main/resources/config-%s.json", target);
        new SpringAnalysis(target).analysis(config);
    }
}
