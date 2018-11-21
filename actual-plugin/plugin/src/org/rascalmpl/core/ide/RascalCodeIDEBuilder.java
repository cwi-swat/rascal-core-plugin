package org.rascalmpl.core.ide;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
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

	private final Future<Evaluator> checkerEvaluator;
	private static final IList EMPTY_LIST = IRascalValueFactory.getInstance().list();

	public RascalCodeIDEBuilder() {
		// this constructor is run on the main thread, and so are the callbacks
		// so we need to construct the evaluator on a seperate thread, to try and avoid freezing the main thread
		checkerEvaluator = new FutureTask<>(() -> {
			try {
				Activator.log("Initializing the rascal-core type checker", new RuntimeException());
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
	public IList compile(IList files, IConstructor pcfg) {
		Evaluator eval;
		try {
			eval = checkerEvaluator.get();
		} catch (InterruptedException | ExecutionException e1) {
			return EMPTY_LIST;
		}
		if (eval == null) {
			return EMPTY_LIST;
		}
		files = filterOutRascalFiles(files, eval.getValueFactory());
		if (files.length() == 0) {
			return EMPTY_LIST;
		}
		synchronized (eval) {
			try {
				return (IList) eval.call("check", files, pcfg);
			} catch (Throwable e) {
				eval.getStdErr().println("check failed for: " + files);
				eval.getStdErr().println("exception: ");
				if (e instanceof StaticError) {
					ReadEvalPrintDialogMessages.staticErrorMessage(eval.getStdErr(), (StaticError) e, new StandardTextWriter(true));
				}
				else if (e instanceof Throw) {
					ReadEvalPrintDialogMessages.throwMessage(eval.getStdErr(), (Throw) e, new StandardTextWriter(true));
				}
				else {
					ReadEvalPrintDialogMessages.throwableMessage(eval.getStdErr(), e, eval.getStackTrace(), new StandardTextWriter(true));
				}
				return EMPTY_LIST;
			}
		}
	}


	@Override
	public IList compileAll(ISourceLocation folder, IConstructor pcfg) {
		if (isIgnoredLocation(folder)) {
			return EMPTY_LIST;
		}
		Evaluator eval;
		try {
			eval = checkerEvaluator.get();
		} catch (InterruptedException | ExecutionException e1) {
			return EMPTY_LIST;
		}
		if (eval == null) {
			return EMPTY_LIST;
		}
		synchronized (eval) {
			try {
				return (IList) eval.call("checkAll", folder, pcfg);
			} catch (Throwable e) {
				eval.getStdErr().println("checkAll failed for: " + folder);
				eval.getStdErr().println("exception: ");
				if (e instanceof StaticError) {
					ReadEvalPrintDialogMessages.staticErrorMessage(eval.getStdErr(), (StaticError) e, new StandardTextWriter(true));
				}
				else if (e instanceof Throw) {
					ReadEvalPrintDialogMessages.throwMessage(eval.getStdErr(), (Throw) e, new StandardTextWriter(true));
				}
				else {
					ReadEvalPrintDialogMessages.throwableMessage(eval.getStdErr(), e, eval.getStackTrace(), new StandardTextWriter(true));
				}
				return EMPTY_LIST;
			}
		}
	}

}
