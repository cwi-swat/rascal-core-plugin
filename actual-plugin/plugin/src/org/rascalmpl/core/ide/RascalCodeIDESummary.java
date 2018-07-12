package org.rascalmpl.core.ide;

import static org.rascalmpl.core.ide.CoreBundleEvaluatorFactory.ERROR_WRITER;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.editor.IDESummaryService;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.control_exceptions.Throw;
import org.rascalmpl.interpreter.staticErrors.StaticError;
import org.rascalmpl.interpreter.utils.ReadEvalPrintDialogMessages;
import org.rascalmpl.library.lang.rascal.boot.IKernel;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.io.StandardTextWriter;

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
			Evaluator eval;
			try {
				eval = checkerEvaluator.get();
			} catch (InterruptedException | ExecutionException e1) {
				Activator.log("Could not calculate outline", e1);
				return null;
			}
			if (eval == null) {
				return null;
			}
			synchronized (eval) {
				try {
					return (IConstructor) eval.call("makeSummary", moduleName, pcfg);

				} catch (Throwable e) {
					ERROR_WRITER.println("makeSummary failed for: " + moduleName);
					ERROR_WRITER.println("exception: ");
					if (e instanceof StaticError) {
						ReadEvalPrintDialogMessages.staticErrorMessage(ERROR_WRITER, (StaticError) e, new StandardTextWriter(true));
					}
					else if (e instanceof Throw) {
						ReadEvalPrintDialogMessages.throwMessage(ERROR_WRITER, (Throw) e, new StandardTextWriter(true));
					}
					else {
						ReadEvalPrintDialogMessages.throwableMessage(ERROR_WRITER, e, eval.getStackTrace(), new StandardTextWriter(true));
					}
					return null;
				}
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
