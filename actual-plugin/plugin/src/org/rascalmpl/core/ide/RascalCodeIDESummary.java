package org.rascalmpl.core.ide;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.editor.IDESummaryService;
import org.rascalmpl.eclipse.nature.ProjectEvaluatorFactory;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.library.lang.rascal.boot.IKernel;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.IString;

public class RascalCodeIDESummary implements IDESummaryService {
	
	private final Future<Evaluator> checkerEvaluator;

    public RascalCodeIDESummary() {

    	// this constructor is run on the main thread, and so are the callbacks
    	// so we need to construct the evaluator on a seperate thread, to try and avoid freezing the main thread
    	checkerEvaluator = new FutureTask<>(() -> {
    		try {
    			Evaluator eval = CoreBundleEvaluatorFactory.construct();
    			eval.doImport(null, "lang::rascalcore::check::Summary");
    			return eval;
    		}
    		catch (Throwable e) {
    			Activator.log("Cannot initialize rascal-core type checker", e);
    			return null;
    		}
    	});
    	// schedule the init on a thread that runs once and finishes after initializing the evaluator
    	Thread lazyInit = new Thread((FutureTask<?>)checkerEvaluator);
    	lazyInit.setDaemon(true);
    	lazyInit.setName("Background initializer for rascal-core evaluator");
    	lazyInit.start();
    }
    

	@Override
	public IConstructor calculate(IKernel kernel, IString moduleName, IConstructor pcfg) {
		try {
			final Evaluator eval = checkerEvaluator.get();
			if (eval == null) {
				return null;
			}
			synchronized (eval) {
				return (IConstructor) eval.call("makeSummary", moduleName, pcfg);
			}
		}
		catch (Throwable t) {
			Activator.log("Could not calculate summary", t);
			return null;
		}
	}

	@Override
	public INode getOutline(IKernel kernel, IConstructor moduleTree) {
		try {
			return kernel.outline(moduleTree);
		}
		catch (Throwable t) {
			Activator.log("Could not calculate outline", t);
			return null;
		}
	}


}
