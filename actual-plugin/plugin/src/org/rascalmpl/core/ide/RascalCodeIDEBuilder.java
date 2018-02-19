package org.rascalmpl.core.ide;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.builder.BuildRascalService;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.control_exceptions.Throw;
import org.rascalmpl.values.uptr.IRascalValueFactory;
import static org.rascalmpl.core.ide.CoreBundleEvaluatorFactory.ERROR_WRITER;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;

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
		} catch (Throwable e) {
			ERROR_WRITER.println("Check failed: " + e.getMessage());
			e.printStackTrace(ERROR_WRITER);
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
		} catch (Throwable e) {
			ERROR_WRITER.println("Check failed: " + e.getMessage());
			e.printStackTrace(ERROR_WRITER);
            return EMPTY_LIST;
		}
	}

}
