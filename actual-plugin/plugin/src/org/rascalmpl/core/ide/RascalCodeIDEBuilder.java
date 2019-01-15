package org.rascalmpl.core.ide;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.builder.BuildRascalService;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.control_exceptions.InterruptException;
import org.rascalmpl.values.uptr.IRascalValueFactory;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

public class RascalCodeIDEBuilder implements BuildRascalService {

	private final Future<Evaluator> checkerEvaluator;
	private static final IList EMPTY_LIST = IRascalValueFactory.getInstance().list();

	public RascalCodeIDEBuilder() {
		// this constructor is run on the main thread, and so are the callbacks
		// so we need to construct the evaluator on a seperate thread, to try and avoid freezing the main thread
		checkerEvaluator = BackgroundInitializer.lazyImport("rascal-core type checker", "lang::rascalcore::check::Checker");
	}

	private static IList filterOutRascalFiles(IList files, IValueFactory vf) {
		IListWriter result = vf.listWriter();
		for (IValue l : files) {
			if (l instanceof ISourceLocation && !isIgnoredLocation((ISourceLocation)l)) {
				result.append(l);
			}
		}
		return result.done();
	}

	private static boolean isIgnoredLocation(ISourceLocation l) {
		return "project".equals(l.getScheme()) && ("rascal".equals(l.getAuthority()) || "rascal-eclipse".equals(l.getAuthority()));
	}
	
	private FutureTask<IList> rascalInterruptableTask(Function<AtomicReference<Evaluator>, IList> calc) {
	    final AtomicReference<Evaluator> currentEvaluator = new AtomicReference<>(null);
        return new FutureTask<IList>(() -> calc.apply(currentEvaluator)) {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (!isDone()) {
                    Evaluator eval = currentEvaluator.get();
                    if (eval != null) {
                        eval.interrupt();
                    }
                }
                return super.cancel(mayInterruptIfRunning);
            }
        };
	}

	@Override
	public FutureTask<IList> compile(IList files, IConstructor pcfg) {
	    return rascalInterruptableTask((currentEvaluator) -> {
            try {
                Evaluator eval = checkerEvaluator.get();
                if (eval == null) {
                    return EMPTY_LIST;
                }

                IList filteredFiles = filterOutRascalFiles(files, eval.getValueFactory());
                if (filteredFiles.length() == 0) {
                    return EMPTY_LIST;
                }

                synchronized (eval) {
                    currentEvaluator.set(eval);
                    try {
                        return (IList) eval.call("check", filteredFiles, pcfg);
                    } catch (InterruptException e) {
                        return EMPTY_LIST;
                    } finally {
                        currentEvaluator.set(null);
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                Activator.log("Rascal type check failed (initializing evaluator)",
                        e instanceof ExecutionException ? e.getCause() : e);
                return EMPTY_LIST;
            } catch (Throwable e) {
                Activator.log("Rascal type check failed (check)", e);
                return EMPTY_LIST;
            }
        });
	}


	@Override
	public FutureTask<IList> compileAll(ISourceLocation folder, IConstructor pcfg) {
	    return rascalInterruptableTask((currentEvaluator) -> {
    		if (isIgnoredLocation(folder)) {
    			return EMPTY_LIST;
    		}
    		try {
    			Evaluator eval = checkerEvaluator.get();
                if (eval == null) {
                    return EMPTY_LIST;
                }
                synchronized (eval) {
                    currentEvaluator.set(eval);
                    try {
                        return (IList) eval.call("checkAll", folder, pcfg);
                    } catch (InterruptException e) {
                        return EMPTY_LIST;
                    } finally {
                        currentEvaluator.set(null);
                    }
                }
    		} catch (InterruptedException | ExecutionException e) {
    			Activator.log("Rascal type check failed (initializing evaluator)", e instanceof ExecutionException ? e.getCause() : e);
    			return EMPTY_LIST;
    		} catch (Throwable e) {
    			Activator.log("Rascal type check failed (checkAll)", e);
    			return EMPTY_LIST;
    		}
	    });
	}

}
