package org.rascalmpl.core.ide;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
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
	
	private final Future<Evaluator> singleEvaluator;
    public RascalCodeIDESummary() {

    	// this constructor is run on the main thread, and so are the callbacks
    	// so we need to construct the evaluator on a seperate thread, to try and avoid freezing the main thread
    	singleEvaluator = new FutureTask<>(() -> {
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
    	// schedule the init on a thread that runs once and finishes after initializing the evaluator
    	Thread lazyInit = new Thread((FutureTask<?>)singleEvaluator);
    	lazyInit.setDaemon(true);
    	lazyInit.setName("Background initializer for evaluator");
    	lazyInit.start();
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

	private Evaluator getEvaluator() {
		try {
			return singleEvaluator.get();
		} catch (InterruptedException | ExecutionException e) {
			return null;
		}
	}
	


	@Override
	public IConstructor calculate(IKernel kernel, IString moduleName, IConstructor pcfg) {
		final Evaluator eval = getEvaluator();
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

	@Override
	public INode getOutline(IKernel kernel, IConstructor moduleTree) {
		return kernel.outline(moduleTree);
	}

}
