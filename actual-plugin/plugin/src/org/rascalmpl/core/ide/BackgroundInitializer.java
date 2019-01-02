package org.rascalmpl.core.ide;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.interpreter.Evaluator;
import io.usethesource.impulse.runtime.RuntimePlugin;

public class BackgroundInitializer {
	public static <T> Future<T> construct(String name, Callable<T> generate) {
		FutureTask<T> result = new FutureTask<>(() -> {
			try {
                return generate.call();
			} catch (Throwable e) {
				Activator.log("Cannot initialize " + name, e);
				return null;
			}
		});
		Thread background = new Thread(result);
		background.setDaemon(true);
		background.setName("Background initializer for: " + name);
		background.start();
		return result;
	}
	
	public static Future<Evaluator> lazyImport(String name, String... modules) {
		return construct(name, () -> {
            RuntimePlugin.getInstance().getConsoleStream().println("Initializing evaluator for: " + name + "...");
            RuntimePlugin.getInstance().getConsoleStream().flush();
            Evaluator eval = CoreBundleEvaluatorFactory.construct();
            for (String m : modules) {
            	eval.doImport(null, m);
            }
            RuntimePlugin.getInstance().getConsoleStream().println("Finished initializing evaluator for: " + name);
            RuntimePlugin.getInstance().getConsoleStream().flush();
            return eval;
		});
	}

}
