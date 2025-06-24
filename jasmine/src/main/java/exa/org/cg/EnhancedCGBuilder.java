package exa.org.cg;

import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.file.FileSinkGEXF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.pag.PAG;
import soot.jimple.spark.pag.VarNode;
import soot.jimple.spark.solver.PropWorklist;
import soot.jimple.spark.solver.Propagator;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.util.Chain;
import soot.util.queue.QueueReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class EnhancedCGBuilder {
    protected static final Logger logger = LoggerFactory.getLogger(CGBuilder.class);
    private Map<String, Set<String>> map;
    private Map<String, Set<Type>> inferCache = new HashMap<>();
    private String path;
    private Set<SootMethodRef> refSet = new HashSet<>();
    private void compute() {
        Chain<SootClass> classes = Scene.v().getApplicationClasses();
        System.out.println("compute functions");
        int a=0,b=0;
        for (SootClass sc : classes) {
            for (SootMethod method : sc.getMethods()) {
                if (!method.isAbstract()) {
                    if (method.getSignature().equals("<org.apache.struts2.components.Component: java.lang.String determineActionURL(java.lang.String,java.lang.String,java.lang.String,javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse,java.util.Map,java.lang.String,boolean,boolean,boolean,boolean)>"))
                        System.out.printf("determineActionURL has %s body\n", method.hasActiveBody()?" ":"not");
                    if (method.hasActiveBody()) {
//                        System.out.println(method.getSignature());
                        a++;
                    } else b++;

                }
            }
        }
        System.out.printf("a=%d,b=%d\n",a,b);
    }
    public EnhancedCGBuilder(String path) {
        this.path = path;
        map = new IoCConfigLoader(Paths.get(path)).getBeanMap();
    }

   private SootMethod getDoFilterMethod() {
        return Scene.v().getSootClass("org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter").getMethod("void doFilter(javax.servlet.ServletRequest,javax.servlet.ServletResponse,javax.servlet.FilterChain)");
    }
    private SootMethod getUIBeanMethod() {
        return Scene.v().getSootClass("org.apache.struts2.components.UIBean").getMethod("void evaluateParams()");
    }
    public void build() {
        initializeSoot();
        List<SootMethod> entryPoints = new ArrayList<>();
        entryPoints.add(getDoFilterMethod());
        entryPoints.add(getUIBeanMethod());
        Scene.v().setEntryPoints(entryPoints);
        PackManager.v().getPack("cg").apply();
        PAG pag = (PAG) Scene.v().getPointsToAnalysis();
        CallGraph cg = Scene.v().getCallGraph();
        int loop = 0;
        int changed;
        do {

            // ② fallback：为空 PTS 的调用点补 CHA 边
            changed = addFallbackEdges(cg, pag);

            // ④ 对增量边做传播
            if (changed > 0) {
                Propagator propagator = new PropWorklist(pag);
                propagator.propagate();   // 只增量迭代
            }
            loop++;
            if (loop >12) break;
            logger.warn("CG build loop: {}, count {}", loop, changed);
        } while (changed > 0);
        compute();
    }
    private int addFallbackEdges(CallGraph cg, PAG pag) {
        int changed = 0;
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
                SootMethodRef ref = ie.getMethodRef();
                if (refSet.contains(ref))
                    continue;
                refSet.add(ref);
                // —— ② 用 CHA 找所有实现 / 覆写版本 ——
                Set<Type> types = infer(ie, src);

                if (types.isEmpty()) continue;            // 兜底仍找不到 → 保持空
                for (Type type : types) {
                    if (type instanceof RefType refType) {
                        Local base = (Local) ((InstanceInvokeExpr) ie).getBase();
                        VarNode baseNode = pag.makeLocalVarNode(base, base.getType(), src);
                        NewExpr newExpr = Jimple.v().newNewExpr(refType);
                        AllocNode allocNode = pag.makeAllocNode(newExpr, type, src);
                        pag.addAllocEdge(allocNode, baseNode);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }
    private Set<Type> infer(InvokeExpr invokeExpr, SootMethod src) {
        Hierarchy hier = Scene.v().getActiveHierarchy();
        SootMethod declared = invokeExpr.getMethod();
        String subSig       = declared.getSubSignature();
        SootClass baseClass = declared.getDeclaringClass();
        String signature = declared.getSignature();
        if (inferCache.containsKey(signature))
            return inferCache.get(signature);
        Set<Type> out = smartInfer( invokeExpr, declared, subSig, baseClass, hier);
        inferCache.put(signature, out);
        return out;
    }
    private Set<Type> smartInfer(InvokeExpr invokeExpr, SootMethod declared, String subSig, SootClass baseClass, Hierarchy hier) {
        Set<Type> out = new HashSet<>();
        if (!baseClass.isApplicationClass())
            return out;

        boolean concrete = baseClass.isConcrete();

        if (concrete) {
            // 1. 只有自己一个实现类
            Collection<SootClass> kids = hier.getSubclassesOf(baseClass);
            if (kids.isEmpty()) {
                Type type = baseClass.getType();
                out.add(type);
                return out;
            }
        }
        // 2. 配置文件定义了具体实现类
        String baseName = baseClass.getName();
        for (String implName: map.getOrDefault(baseName, new HashSet<>())) {
            SootClass impl = Scene.v().getSootClass(implName);
            if (impl.declaresMethod(subSig)) {
                out.add(impl.getType());
            }
        }
        if (!out.isEmpty())
            return out;
        // 3. 没有配置文件定义，用 CHA 兜底
        return getCHA(subSig, baseClass, hier);
    }

    private Set<Type> getCHA(String subSig, SootClass baseClass, Hierarchy hier) {
        Set<Type> targets = new HashSet<>();

// ---------- 1. 枚举可能实现 ----------
        Collection<SootClass> candidates;
        if (baseClass.isInterface()) {
            // 接口：取所有实现者（含子接口、接口自身）再过滤 concrete classes
            candidates = new HashSet<>();
            // a) 接口自己
            candidates.add(baseClass);
            // b) 实现类 / 子接口
            candidates.addAll(hier.getImplementersOf(baseClass));
        } else {
            // 普通类：自身 + 子类
            candidates = hier.getSubclassesOfIncluding(baseClass);
        }

// ---------- 2. 筛选出真正声明了该方法的 concrete 类 ----------
        for (SootClass sub : candidates) {
            if (sub.isInterface() || sub.isPhantom())   // 跳过接口 & phantom
                continue;

            // 声明了同签名方法才算实现/覆写
            if (sub.declaresMethod(subSig)) {
                targets.add(sub.getType());
            }
        }
        return targets;
    }
    private void initializeSoot() {
        G.reset();
        List<String> dir = new ArrayList<>();
        dir.add(path);

        Options.v().setPhaseOption("cg.spark", "on");
        Options.v().setPhaseOption("cg.spark", "verbose:true");
        Options.v().setPhaseOption("cg.spark", "enabled:true");

        Options.v().setPhaseOption("cg.spark", "propagator:worklist");
        Options.v().setPhaseOption("cg.spark", "simple-edges-bidirectional:false");
        Options.v().setPhaseOption("cg.spark", "on-fly-cg:true");
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


    public static void main(String[] args) throws IOException {
        EnhancedCGBuilder builder = new EnhancedCGBuilder("../benchmark/struts-013");
        builder.build();
    }
}
