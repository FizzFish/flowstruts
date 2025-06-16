package soot.jimple.infoflow.examples;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import soot.jimple.infoflow.IInfoflow;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.sourcesSinks.manager.DefaultSourceSinkManager;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;


/**
 * Simple example that runs FlowDroid on a struts-core JAR file.
 * <p>
 * Usage: {@code java StrutsInfoflowExample <struts-jar> <rt.jar>}
 *
 * The example uses a fixed entry point inside the Struts framework as well as
 * very small source/sink definitions for demonstration purposes.
 */
public class StrutsInfoflowExample {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: StrutsInfoflowExample <struts-jar> <rt.jar>");
            return;
        }
        String appPath = args[0];
        String libPath = args[1];
        test005(appPath, libPath);
//        test045(appPath, libPath);
    }

    private static void test005(String appPath, String libPath) {
        List<String> entryPoints = new ArrayList<>();
        // String doIntercept(ActionInvocation invocation)
        String strutsEntry = "<org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter: void doFilter(javax.servlet.ServletRequest,javax.servlet.ServletResponse,javax.servlet.FilterChain)>";
        String interceptEntry = "<com.opensymphony.xwork2.interceptor.ParametersInterceptor: java.lang.String doIntercept(com.opensymphony.xwork2.ActionInvocation)>";
        entryPoints.add(strutsEntry);
        entryPoints.add(interceptEntry);

        List<String> sources = Arrays.asList(
                "<com.opensymphony.xwork2.interceptor.ParametersInterceptor: java.util.Map retrieveParameters(com.opensymphony.xwork2.ActionContext)>"
        );
        List<String> sinks = Arrays.asList(
                "<com.opensymphony.xwork2.util.ValueStack: void setValue(java.lang.String,java.lang.Object)>"
        );

        IInfoflow infoflow = new Infoflow();
        infoflow.getConfig().setLogSourcesAndSinks(true);
        File taintWrapperFile = new File("soot-infoflow/EasyTaintWrapperSource.txt");
        try {
            infoflow.setTaintWrapper(new EasyTaintWrapper(taintWrapperFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

//        ISourceSinkManager ssm = new DefaultSourceSinkManager(sources, sinks);
//        infoflow.computeInfoflow(appPath, libPath, interceptEntry, ssm);
        infoflow.computeInfoflow(appPath, libPath, entryPoints, sources, sinks);

        System.out.println(infoflow.getResults());
    }

    private static void test045(String appPath, String libPath) {
        List<String> entryPoints = new ArrayList<>();
        String strutsEntry = "<org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter: void doFilter(javax.servlet.ServletRequest,javax.servlet.ServletResponse,javax.servlet.FilterChain)>";
        entryPoints.add(strutsEntry);

        List<String> sources = Arrays.asList(
                "<org.apache.commons.fileupload.RequestContext: java.lang.String getContentType()>"
        );

        List<String> sinks = Arrays.asList(
                "<com.opensymphony.xwork2.util.TextParseUtil: java.lang.String translateVariables(java.lang.String,com.opensymphony.xwork2.util.ValueStack)>"
        );

        ISourceSinkManager ssm = new DefaultSourceSinkManager(sources, sinks);

        IInfoflow infoflow = new Infoflow();
        infoflow.getConfig().setLogSourcesAndSinks(true);
        infoflow.computeInfoflow(appPath, libPath, strutsEntry, ssm);

        System.out.println(infoflow.getResults());
    }
}
