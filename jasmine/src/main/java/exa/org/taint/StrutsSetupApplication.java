package exa.org.taint;

import exa.org.edge.StrutsCreateEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.Main;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.data.parsers.PermissionMethodParser;
import soot.jimple.infoflow.android.iccta.IccInstrumenter;
import soot.jimple.infoflow.android.source.AccessPathBasedSourceSinkManager;
import soot.jimple.infoflow.solver.memory.DefaultMemoryManagerFactory;
import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkDefinitionProvider;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.jimple.infoflow.taintWrappers.ITaintWrapperDataFlowAnalysis;
import soot.options.Options;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Setup class for running Struts taint analysis.
 * This is largely based on {@link SpringSetupApplication} but uses
 * {@link StrutsCreateEdge} to build the entry points.
 */
public class StrutsSetupApplication implements ITaintWrapperDataFlowAnalysis {
    private final Logger logger = LoggerFactory.getLogger(StrutsSetupApplication.class);
    private ITaintPropagationWrapper taintWrapper;
    private InfoflowAndroidConfiguration config;

    protected IccInstrumenter iccInstrumenter = null;

    @Override
    public void setTaintWrapper(ITaintPropagationWrapper taintWrapper) {
        this.taintWrapper = taintWrapper;
    }

    @Override
    public ITaintPropagationWrapper getTaintWrapper() {
        return taintWrapper;
    }

    public void runInfoflow(String sourceSinkFile) {
        try {
            ISourceSinkDefinitionProvider parser = PermissionMethodParser.fromFile(new File(sourceSinkFile));
            config = new InfoflowAndroidConfiguration();

            IMyInfoFlow infoflow = createInfoflow();
            ResultAggreator resultAggregator = new ResultAggreator();
            infoflow.addResultsAvailableHandler(resultAggregator);

            initializeSoot();
            createMainMethod();

            AccessPathBasedSourceSinkManager sourceSinkManager = new AccessPathBasedSourceSinkManager(
                    parser.getSources(), parser.getSinks(), Collections.emptySet(), config, null);
            infoflow.runAnalysis(sourceSinkManager, null);

        } catch (IOException e) {
            logger.error("read file error{}", (Object) e.getStackTrace());
        }
    }

    private void createMainMethod() {
        if (StrutsAnalysis.analysisAlgorithm.equals("jasmine")) {
            StrutsCreateEdge edge = new StrutsCreateEdge();
            edge.initCallGraph();
            Scene.v().setMainClass(edge.projectMainMethod.getDeclaringClass());
        } else {
            String benchmark = StrutsAnalysis.v().getBenchmark();
            ArrayList<SootMethod> entryPoints = EntryPointConfig.getEntryPoints(benchmark);
            Scene.v().setEntryPoints(entryPoints);
        }
    }

    private IMyInfoFlow createInfoflow() {
        IMyInfoFlow info = new MyInfoFlow();
        info.setConfig(config);
        info.setTaintWrapper(taintWrapper);
        info.setMemoryManagerFactory(new DefaultMemoryManagerFactory());
        return info;
    }

    private void initializeSoot() {
        logger.info("Initializing Soot...");

        G.reset();
        List<String> dir = new ArrayList<>();
        dir.add(StrutsAnalysis.v().getProcessDir());

        if (StrutsAnalysis.analysisAlgorithm.equals("spark") || StrutsAnalysis.analysisAlgorithm.equals("jasmine")) {
            Options.v().setPhaseOption("cg.spark", "on");
            Options.v().setPhaseOption("cg.spark", "verbose:true");
            Options.v().setPhaseOption("cg.spark", "enabled:true");
            Options.v().setPhaseOption("cg.spark", "propagator:worklist");
            Options.v().setPhaseOption("cg.spark", "simple-edges-bidirectional:false");
            Options.v().setPhaseOption("cg.spark", "on-fly-cg:true");
            Options.v().setPhaseOption("cg.spark", "double-set-old:hybrid");
            Options.v().setPhaseOption("cg.spark", "double-set-new:hybrid");
            Options.v().setPhaseOption("cg.spark", "set-impl:double");
            Options.v().setPhaseOption("cg.spark", "apponly:true");
            Options.v().setPhaseOption("cg.spark", "simple-edges-bidirectional:false");
            Options.v().set_verbose(true);
        } else {
            Options.v().setPhaseOption("cg.cha", "on");
            Options.v().setPhaseOption("cg.cha", "enabled:true");
            Options.v().setPhaseOption("cg.cha", "verbose:true");
            Options.v().setPhaseOption("cg.cha", "apponly:true");
            Options.v().set_verbose(true);
        }

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
        Options.v().set_soot_classpath(getSootClassPath());
        Main.v().autoSetOptions();
        configureCallgraph();

        Scene.v().loadNecessaryClasses();
        PackManager.v().getPack("wjpp").apply();
    }

    private String getClasspath(String workDir) {
        String sootCp = Scene.v().getSootClassPath();
        String sootCps = "";
        try (Stream<Path> paths = Files.walk(Paths.get(workDir))) {
            sootCps = paths.filter(path -> path.toString().endsWith("jar"))
                    .map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator));
        } catch (IOException e) {
        }
        sootCp += File.pathSeparator + sootCps;
        return sootCp;
    }

    private static String getSootClassPath() {
        return StrutsAnalysis.v().getLibPath();
    }

    private void configureCallgraph() {
    }
}
