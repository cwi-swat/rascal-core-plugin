package org.rascalmpl.core.ide;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.editor.IDESummaryService;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.library.lang.rascal.boot.IKernel;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.io.StandardTextWriter;

public class RascalCodeIDESummary implements IDESummaryService {
	
	private final Future<Evaluator> checkerEvaluator;
	private final Future<Evaluator> outlineEvaluator;

    public RascalCodeIDESummary() {
    	// this constructor is run on the main thread, and so are the callbacks
    	// so we need to construct the evaluator on a seperate thread, to try and avoid freezing the main thread
		checkerEvaluator = BackgroundInitializer.lazyImport("rascal-core type checker - summary", "lang::rascalcore::check::Summary");
		outlineEvaluator = BackgroundInitializer.lazyImport("outline", "lang::rascal::ide::Outline");
    }


	@Override
	public IConstructor calculate(IKernel kernel, IString moduleName, IConstructor pcfg) {
		try {
			Evaluator eval = checkerEvaluator.get();
			if (eval == null) {
				Activator.log("Could not calculate summary due to missing evaluator", null);
				return null;
			}
			synchronized (eval) {
                return vprint(eval, (IConstructor) eval.call("makeSummary", moduleName, pcfg));
			}
		} 
		catch (InterruptedException | ExecutionException e1) {
			Activator.log("Could not calculate makeSummary due to failure of constructing the evaluator", e1);
			return null;
        } catch (Throwable e) {
            Activator.log("makeSummary failed", e);
            return null;
        }
	}
	


	private IConstructor vprint(Evaluator eval, IConstructor call) {
		eval.getStdErr().println("makeSummary returned: ");
		StandardTextWriter writer = new StandardTextWriter(true);
		try {
			writer.write(call, eval.getStdErr());
		} catch (IOException e) {
            Activator.log("failure to print makeSummary result", e);
		}
		return call;
	}


	@Override
	public INode getOutline(IKernel kernel, IConstructor moduleTree) {
		try {
			Evaluator eval = outlineEvaluator.get();
			if (eval == null) {
				Activator.log("Could not calculate outline due to missing evaluator", null);
				return null;
			}
			synchronized (eval) {
                return (INode) eval.call((IRascalMonitor) null, "outline", moduleTree);
			}
		} 
		catch (InterruptedException | ExecutionException e1) {
			Activator.log("Could not calculate outline due to failure of constructing the evaluator", e1);
			return null;
        } catch (Throwable e) {
            Activator.log("outline failed", e);
            return null;
        }
	}

}
