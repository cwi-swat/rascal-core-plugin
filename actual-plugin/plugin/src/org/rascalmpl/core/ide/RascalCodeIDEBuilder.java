package org.rascalmpl.core.ide;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.builder.BuildRascalService;
import org.rascalmpl.eclipse.editor.IDEServicesModelProvider;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.values.uptr.IRascalValueFactory;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;

public class RascalCodeIDEBuilder implements BuildRascalService {
	
	private final Future<Evaluator> checkerEvaluator;
	private static final IList EMPTY_LIST = IRascalValueFactory.getInstance().list();

	public RascalCodeIDEBuilder() {
    	// this constructor is run on the main thread, and so are the callbacks
    	// so we need to construct the evaluator on a seperate thread, to try and avoid freezing the main thread
    	checkerEvaluator = new FutureTask<>(() -> {
    		try {
    			Evaluator eval = CoreBundleEvaluatorFactory.construct();
    			eval.doImport(null, "lang::rascalcore::check::Checker");
    			return eval;
    		}
    		catch (Throwable e) {
    			Activator.log("Cannot initialize rascal-core type checker", e);
    			return null;
    		}
    	});
    	Thread t = new Thread((Runnable) checkerEvaluator);
    	t.setName("Background checker initialization");
    	t.setDaemon(true);
    	t.start();
	}

	@Override
	public IList compile(IList files, IConstructor pcfg) {
		try {
			Evaluator eval = checkerEvaluator.get();
			if (eval == null) {
				return EMPTY_LIST;
			}
			synchronized (eval) {
                return (IList) eval.call("check", files, pcfg);
			}
		} catch (InterruptedException | ExecutionException e) {
            return EMPTY_LIST;
		}
	}

	@Override
	public IList compileAll(ISourceLocation folder, IConstructor pcfg) {
		try {
			Evaluator eval = checkerEvaluator.get();
			if (eval == null) {
				return EMPTY_LIST;
			}
			synchronized (eval) {
				return (IList) eval.call("checkAll", folder, pcfg);
			}
		} catch (InterruptedException | ExecutionException e) {
            return EMPTY_LIST;
		}
	}

}
