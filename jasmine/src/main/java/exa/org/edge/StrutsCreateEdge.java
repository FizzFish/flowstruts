package exa.org.edge;

import soot.*;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.NullConstant;
import soot.Local;
import soot.util.Chain;

import java.util.Arrays;

/**
 * Simplified edge creator for Struts applications. It builds a dummy main
 * method which invokes typical Struts entry points.
 */
public class StrutsCreateEdge {
    protected String dummyClassName = "synthetic.struts.dummyMainClass";
    public SootMethod projectMainMethod;

    /**
     * Initialize the call graph by creating entry points.
     */
    public void initCallGraph() {
        generateEntryPoints();
    }

    private void generateEntryPoints() {
        SootClass sClass = new SootClass(dummyClassName, Modifier.PUBLIC);
        sClass.setSuperclass(Scene.v().getSootClass("java.lang.Object"));
        Scene.v().addClass(sClass);
        sClass.setApplicationClass();
        SootMethod mainMethod = new SootMethod("main",
                Arrays.asList(ArrayType.v(RefType.v("java.lang.String"), 1)),
                VoidType.v(), Modifier.PUBLIC | Modifier.STATIC);
        sClass.addMethod(mainMethod);
        JimpleBody jimpleBody = createMainBody(mainMethod);
        mainMethod.setActiveBody(jimpleBody);
        projectMainMethod = mainMethod;
    }

    private JimpleBody createMainBody(SootMethod method) {
        JimpleBody body = Jimple.v().newBody(method);
        Chain<Local> locals = body.getLocals();
        Local args = Jimple.v().newLocal("args", ArrayType.v(RefType.v("java.lang.String"), 1));
        locals.add(args);
        body.getUnits().add(Jimple.v().newIdentityStmt(args, Jimple.v().newParameterRef(args.getType(), 0)));

        // Struts filter
        SootClass filterClass = Scene.v().getSootClass("org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter");
        Local filter = Jimple.v().newLocal("filter", filterClass.getType());
        locals.add(filter);
        body.getUnits().add(Jimple.v().newAssignStmt(filter, Jimple.v().newNewExpr(filterClass.getType())));
        body.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(filter, filterClass.getMethod("void <init>()").makeRef())));
        SootMethod doFilter = filterClass.getMethod("void doFilter(javax.servlet.ServletRequest,javax.servlet.ServletResponse,javax.servlet.FilterChain)");
        body.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(filter, doFilter.makeRef(), Arrays.asList(NullConstant.v(), NullConstant.v(), NullConstant.v()))));

        // ParametersInterceptor
        SootClass interceptorClass = Scene.v().getSootClass("com.opensymphony.xwork2.interceptor.ParametersInterceptor");
        Local interceptor = Jimple.v().newLocal("interceptor", interceptorClass.getType());
        locals.add(interceptor);
        body.getUnits().add(Jimple.v().newAssignStmt(interceptor, Jimple.v().newNewExpr(interceptorClass.getType())));
        body.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(interceptor, interceptorClass.getMethod("void <init>()").makeRef())));
        SootMethod doIntercept = interceptorClass.getMethod("java.lang.String doIntercept(com.opensymphony.xwork2.ActionInvocation)");
        body.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(interceptor, doIntercept.makeRef(), Arrays.asList(NullConstant.v()))));

        body.getUnits().add(Jimple.v().newReturnVoidStmt());
        return body;
    }
}
