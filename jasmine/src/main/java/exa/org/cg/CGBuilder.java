package exa.org.cg;

import exa.org.taint.StrutsAnalysis;
import exa.org.utils.JimpleUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.spark.pag.PAG;
import soot.jimple.spark.pag.VarNode;
import soot.jimple.spark.solver.PropWorklist;
import soot.jimple.spark.solver.Propagator;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.options.SparkOptions;
import soot.util.Chain;
import soot.util.queue.QueueReader;

import java.util.*;

public class CGBuilder {
    private SootMethod projectMainMethod;
    private void compute() {
        Chain<SootClass> classes = Scene.v().getApplicationClasses();
        System.out.println("compute functions");
        int a=0,b=0;
        for (SootClass sc : classes) {
            for (SootMethod method : sc.getMethods()) {
                if (!method.isAbstract()) {
                    if (method.hasActiveBody()) {
//                        System.out.println(method.getSignature());
                        a++;
                    } else b++;

                }
            }
        }
        System.out.printf("a=%d,b=%d\n",a,b);
    }
    private void generateEntryPoints() {
        SootClass sClass = new SootClass("synthetic.struts.dummyMainClass", Modifier.PUBLIC);
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
//        SootMethod initMethod = filterClass.getMethod("void init(javax.servlet.FilterConfig)");
//        body.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(filter, initMethod.makeRef(), Arrays.asList(NullConstant.v()))));
        SootMethod doFilter = filterClass.getMethod("void doFilter(javax.servlet.ServletRequest,javax.servlet.ServletResponse,javax.servlet.FilterChain)");
        body.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(filter, doFilter.makeRef(), Arrays.asList(NullConstant.v(), NullConstant.v(), NullConstant.v()))));

        body.getUnits().add(Jimple.v().newReturnVoidStmt());
        return body;
    }
    private void initializeSoot() {
        G.reset();
        List<String> dir = new ArrayList<>();
        dir.add("../benchmark/struts-045");

        Options.v().setPhaseOption("cg.spark", "on");
        Options.v().setPhaseOption("cg.spark", "verbose:true");
        Options.v().setPhaseOption("cg.spark", "enabled:true");

        Options.v().setPhaseOption("cg.spark", "propagator:worklist");
        Options.v().setPhaseOption("cg.spark", "simple-edges-bidirectional:false");
        Options.v().setPhaseOption("cg.spark", "on-fly-cg:true");
//        Options.v().setPhaseOption("cg.spark", "dump-pag:true");
        Options.v().setPhaseOption("cg.spark", "dump-html:true");
        Options.v().setPhaseOption("cg.spark", "double-set-old:hybrid");
        Options.v().setPhaseOption("cg.spark", "double-set-new:hybrid");
        Options.v().setPhaseOption("cg.spark", "set-impl:double");
        Options.v().setPhaseOption("cg.spark", "apponly:true");
        Options.v().setPhaseOption("cg.spark", "simple-edges-bidirectional:false");
        Options.v().set_verbose(true);

        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_whole_program(true);
        Options.v().set_process_dir(dir);
        Options.v().set_src_prec(Options.src_prec_apk_class_jimple);
        Options.v().set_keep_offset(false);
        Options.v().set_keep_line_number(true);
        Options.v().set_throw_analysis(Options.throw_analysis_dalvik);
        Options.v().set_ignore_resolution_errors(true);
        Options.v().setPhaseOption("jb", "use-original-names:true");
        Options.v().set_soot_classpath("../benchmark/rt.jar");
        Main.v().autoSetOptions();

        Scene.v().loadNecessaryClasses();
        PackManager.v().getPack("wjpp").apply();
    }
    private void showCG(CallGraph cg) {
        for (Edge e : cg) {
            System.out.println(e.getSrc() + "  →  " + e.getTgt());
        }
    }
    private void build() {
        initializeSoot();
        generateEntryPoints();
        Scene.v().setMainClass(projectMainMethod.getDeclaringClass());
        PackManager.v().getPack("cg").apply();
        PointsToAnalysis pag = Scene.v().getPointsToAnalysis();
        CallGraph cg = Scene.v().getCallGraph();
        int loop = 0;
        boolean changed;
        do {
            // ② fallback：为空 PTS 的调用点补 CHA 边
            changed = addFallbackEdges(cg, pag);

            // ④ 对增量边做传播
            if (changed) {
//                SparkOptions sopts = new SparkOptions(PhaseOptions.v().getPhaseOptions("cg.spark"));
                Propagator propagator = new PropWorklist((PAG) pag);
                propagator.propagate();   // 只增量迭代
            }
            loop++;

        } while (changed);
        System.out.println(loop);
        compute();
//        showCG(cg);
    }
    private List<SootMethod> getCHA(InvokeExpr invokeExpr) {
        Hierarchy hier = Scene.v().getActiveHierarchy();
        SootMethod declared = invokeExpr.getMethod();
        String subSig       = declared.getSubSignature();
        SootClass container = declared.getDeclaringClass();

        List<SootMethod> targets = new ArrayList<>();

// ---------- 1. 枚举可能实现 ----------
        Collection<SootClass> candidates;
        if (container.isInterface()) {
            // 接口：取所有实现者（含子接口、接口自身）再过滤 concrete classes
            candidates = new HashSet<>();
            // a) 接口自己
            candidates.add(container);
            // b) 实现类 / 子接口
            candidates.addAll(hier.getImplementersOf(container));
        } else {
            // 普通类：自身 + 子类
            candidates = hier.getSubclassesOfIncluding(container);
        }

// ---------- 2. 筛选出真正声明了该方法的 concrete 类 ----------
        for (SootClass sub : candidates) {
            if (sub.isInterface() || sub.isPhantom())   // 跳过接口 & phantom
                continue;

            // 声明了同签名方法才算实现/覆写
            if (sub.declaresMethod(subSig)) {
                SootMethod impl = sub.getMethodUnsafe(subSig);
                if (impl != null && impl.isConcrete())
                    targets.add(impl);
            }
        }
        return targets;
    }
    private boolean addFallbackEdges(CallGraph cg, PointsToAnalysis pag) {
        boolean changed = false;
        List<SootMethod> newCallees = new ArrayList<>();

        Hierarchy hier = Scene.v().getActiveHierarchy();

        // —— ① 遍历所有已知方法与调用点 ——
        for (QueueReader<MethodOrMethodContext> it = Scene.v().getReachableMethods().listener(); it.hasNext(); ) {
            SootMethod src = it.next().method();
            if (!src.getDeclaringClass().isApplicationClass()) continue;
            if (!src.isConcrete()) continue;

            Body body = src.retrieveActiveBody();           // 已有 body
            // findReceivers
            for (Unit u : body.getUnits()) {
                Stmt stmt = (Stmt) u;
                if (!stmt.containsInvokeExpr()) continue;   // 非调用

                // 若已有 callee，跳过（Spark 已连）
                if (cg.edgesOutOf(u).hasNext()) continue;

                InvokeExpr ie = stmt.getInvokeExpr();
                if (!(ie instanceof InstanceInvokeExpr)) continue; // static ≠ 虚调用

                // —— ② 用 CHA 找所有实现 / 覆写版本 ——
                List<SootMethod> targets = getCHA(ie);

                if (targets.isEmpty()) continue;            // 兜底仍找不到 → 保持空
                for (SootMethod target : targets) {
                    cg.addEdge(new Edge(src.method(), u, target, Kind.VIRTUAL));
                }
                changed = true;
            }
        }

        return changed;
    }

    public static void main(String[] args) {
        CGBuilder builder = new CGBuilder();
        builder.build();
    }
}
