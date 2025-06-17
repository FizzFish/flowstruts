package exa.org.taint;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;

public class SpringAnalysis {
    public static  String SOURCE_FILE_NAME ;
    public static  String MAIN_CLASS;
    public static  String EDGE_CONFIG_PROPERTIES;
    public static String benchmark = "mall-admin";
    public static String analysisAlgorithm = "jasmine";
    private void loadConstant() throws IOException {
        String configFile = "src/main/resources/config.json";
        String configFileInfo = FileUtils.readFileToString(new File(configFile), "UTF-8");
        Gson gson = new Gson();
        HashMap<String, String> map =  gson.fromJson(configFileInfo, HashMap.class);
        SOURCE_FILE_NAME = map.get("source");
        MAIN_CLASS = map.get("main_class");
        EDGE_CONFIG_PROPERTIES = map.get("edge_config");
    }
    public void analysis() throws IOException {
        //Load the path information in source.json file.
        loadConstant();
        //SetUpApplication implements a common interface that supports all data flow analysis of taint wrappers.
        SpringSetupApplication application = new SpringSetupApplication();

        //Create and set up a taint wrapper

        File taintWrapperFile = new File(System.getProperty("user.dir") + File.separator + "EasyTaintWrapperSource.txt");
        application.setTaintWrapper((new EasyTaintWrapper(taintWrapperFile)));

        //The main part of the program
        application.runInfoflow(System.getProperty("user.dir") + File.separator + "SourcesAndSinks-"+ benchmark +".txt");
    }
    public static void main(String []args) throws IOException {
        new SpringAnalysis().analysis();
    }
}
