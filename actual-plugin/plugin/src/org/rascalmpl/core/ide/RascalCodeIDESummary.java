package org.rascalmpl.core.ide;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.editor.IDESummaryService;
import org.rascalmpl.eclipse.nature.ProjectEvaluatorFactory;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.library.lang.rascal.boot.IKernel;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;

public class RascalCodeIDESummary implements IDESummaryService {
	
	private final Future<Evaluator> checkerEvaluator;
	private final Future<Evaluator> outlineEvaluator;

    public RascalCodeIDESummary() {

    	// this constructor is run on the main thread, and so are the callbacks
    	// so we need to construct the evaluator on a seperate thread, to try and avoid freezing the main thread
    	checkerEvaluator = new FutureTask<>(() -> {
    		try {
    			Bundle coreBundle = findRascalCoreBundle();
    			Evaluator eval = ProjectEvaluatorFactory.getInstance().getBundleEvaluator(coreBundle);
    			addNestedJarsToBundle(eval, coreBundle);
    			eval.doImport(null, "lang::rascalcore::check::Summary");
    			eval.doImport(null, "lang::rascalcore::check::Checker");
    			return eval;
    		}
    		catch (Throwable e) {
    			Activator.log("Cannot initialize rascal-core type checker", e);
    			return null;
    		}
    	});
    	outlineEvaluator = new FutureTask<>(() -> {
    		try {
    			Evaluator eval = ProjectEvaluatorFactory.getInstance().getEvaluator(null);
    			eval.doImport(null, "lang::rascal::ide::Outline");
    			return eval;
    		}
    		catch (Throwable e) {
    			Activator.log("Cannot initialize outline evaluator", e);
    			return null;
    		}
    	});
    	// schedule the init on a thread that runs once and finishes after initializing the evaluator
    	Thread lazyInit = new Thread((FutureTask<?>)checkerEvaluator);
    	lazyInit.setDaemon(true);
    	lazyInit.setName("Background initializer for rascal-core evaluator");
    	lazyInit.start();
    	Thread lazyInit2 = new Thread((FutureTask<?>)outlineEvaluator);
    	lazyInit2.setDaemon(true);
    	lazyInit2.setName("Background initializer for outline evaluator ");
    	lazyInit2.start();
    }
    
    private static void addNestedJarsToBundle(Evaluator eval, Bundle coreBundle) {
    	//URIUtil.correctLocation("plugin", bundle.getSymbolicName();
    	Enumeration<URL> ents = coreBundle.findEntries("jars/", "*.jar", false);
    	while (ents.hasMoreElements()) {
    		URL nestedJar = ents.nextElement();
    		try {
				URL unpacked = FileLocator.toFileURL(nestedJar);
				String jarFile = new File(URIUtil.fromURL(unpacked)).getAbsolutePath();
				ProjectEvaluatorFactory.addJarToSearchPath(URIUtil.createFileLocation(jarFile), eval);
			} catch (IOException | URISyntaxException e) {
				Activator.log("Cannot load nested jar: " + nestedJar, e);
			}
    	}
	}

	private static Bundle findRascalCoreBundle() {
    	return Platform.getBundle("org.rascalmpl.rascal_core_bundle");
    }

	@Override
	public IConstructor calculate(IKernel kernel, IString moduleName, IConstructor pcfg) {
		try {
			final Evaluator eval = checkerEvaluator.get();
			if (eval == null) {
				return null;
			}
			synchronized (eval) {
				IValue tmodel = eval.call("rascalTModelFromName", "lang::rascalcore::check::Checker",new HashMap<>(), moduleName, pcfg);
				if (tmodel == null) {
					return null;
				}
				return (IConstructor) eval.call("makeSummary", tmodel, moduleName);
			}
		}
		catch (Throwable t) {
			Activator.log("Could not calculate outline", t);
			return null;
		}
	}

	@Override
	public INode getOutline(IKernel kernel, IConstructor moduleTree) {
		try {
			final Evaluator eval = outlineEvaluator.get();
            if (eval == null) {
                return null;
            }
			synchronized (eval) {
				return (INode) eval.call("outline", moduleTree);
			}
		}
		catch (Throwable t) {
			Activator.log("Could not calculate outline", t);
			return null;
		}
	}


}
