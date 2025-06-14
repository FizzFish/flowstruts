package soot.jimple.infoflow.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import soot.jimple.infoflow.IInfoflow;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator;
import soot.jimple.infoflow.sourcesSinks.manager.DefaultSourceSinkManager;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;

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

        List<String> entryPoints = new ArrayList<>();
        entryPoints.add("<org.apache.struts.action.StrutsPrepareAndExecuteFilter: void doFilter(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse,javax.servlet.FilterChain)>");

        List<String> sources = Arrays.asList(
                "<javax.servlet.http.HttpServletRequest: java.lang.String getParameter(java.lang.String)>",
                "<org.apache.commons.fileupload.RequestContext: java.lang.String getContentType()>"
        );

        List<String> sinks = Arrays.asList(
                "<com.opensymphony.xwork2.util.TextParseUtil: java.lang.String translateVariables(java.lang.String,com.opensymphony.xwork2.util.ValueStack)>"
        );

        ISourceSinkManager ssm = new DefaultSourceSinkManager(sources, sinks);

        IInfoflow infoflow = new Infoflow();
        infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(entryPoints), ssm);

        System.out.println(infoflow.getResults());
    }
}
