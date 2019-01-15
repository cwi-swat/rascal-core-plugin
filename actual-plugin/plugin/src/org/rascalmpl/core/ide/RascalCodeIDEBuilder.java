package org.rascalmpl.core.ide;

import java.sql.Time;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.builder.BuildRascalService;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.control_exceptions.Throw;
import org.rascalmpl.interpreter.staticErrors.StaticError;
import org.rascalmpl.interpreter.utils.ReadEvalPrintDialogMessages;
import org.rascalmpl.values.uptr.IRascalValueFactory;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.io.StandardTextWriter;

public class RascalCodeIDEBuilder implements BuildRascalService {

	private final CompletableFuture<Evaluator> checkerEvaluator;
	private static final IList EMPTY_LIST = IRascalValueFactory.getInstance().list();
	private final ExecutorService cancelationScheduler = Executors.newSingleThreadExecutor();
	private final ExecutorService evalScheduler = Executors.newCachedThreadPool();


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

	@Override
	public CompletableFuture<IList> compile(IList files, IConstructor pcfg) {
		CompletableFuture<IList> result = new CompletableFuture<IList>();
		
		cancelationScheduler.execute(() -> {
			Evaluator eval = getEvaluatorOrComplete(result);
			if (eval != null) {
                IList filteredFiles = filterOutRascalFiles(files, eval.getValueFactory());
                if (filteredFiles.length() == 0) {
                    result.complete(EMPTY_LIST);
                    return;
                }
                callEvalInterruptable(result, eval, "check", filteredFiles, pcfg);
			}
		});

		return result;
	}

	@Override
	public CompletableFuture<IList> compileAll(ISourceLocation folder, IConstructor pcfg) {
		if (isIgnoredLocation(folder)) {
			return CompletableFuture.completedFuture(EMPTY_LIST);
		}

		CompletableFuture<IList> result = new CompletableFuture<IList>();
		
		cancelationScheduler.execute(() -> {
			Evaluator eval = getEvaluatorOrComplete(result);
			if (eval != null) {
                callEvalInterruptable(result, eval, "checkAll", folder, pcfg);
			}
		});

		return result;
	}
	
	private Evaluator getEvaluatorOrComplete(CompletableFuture<IList> result) {
		while (true) {
			try {
				if (result.isCancelled()) {
					return null;
				}
				return checkerEvaluator.get(1, TimeUnit.SECONDS);
			}
			catch (TimeoutException e) {
				continue;
			} 
			catch (InterruptedException | ExecutionException e) {
				Activator.log("Rascal type check failed (initializing evaluator)", e instanceof ExecutionException ? e.getCause() : e );
				result.complete(EMPTY_LIST);
				return null;
			}
		}
	}
	
	private void callEvalInterruptable(CompletableFuture<IList> result, Evaluator eval, String functionName, IValue... args) {
		synchronized (eval) {
			// now we have the lock, we'll call stuff on our own thread, so that we can send an interrupt to the evaluator
			Future<IList> calculatedResult = evalScheduler.submit(() -> (IList)eval.call(functionName, args));
			while (true) {
				try {
					if (result.isCancelled()) {
						eval.interrupt();
						calculatedResult.cancel(true);
                        try {
                        	// now wait for the call to finish, as we can only release the eval lock afterwards
                            calculatedResult.get();
                        } catch (Throwable e) {
                        }
						return;
					}
					IList actualResult = calculatedResult.get(1, TimeUnit.SECONDS);
					result.complete(actualResult == null ? EMPTY_LIST : actualResult);
					return;
				}
				catch (TimeoutException e) {
					continue;
				} catch (InterruptedException | ExecutionException e) {
					Activator.log("Rascal type check failed (check)", e instanceof ExecutionException ? e.getCause() : e );
					result.complete(EMPTY_LIST);
					return;
				}
			}
		}
	}



}
