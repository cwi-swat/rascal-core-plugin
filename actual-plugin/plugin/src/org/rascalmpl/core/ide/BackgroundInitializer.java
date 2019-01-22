package org.rascalmpl.core.ide;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.util.ThreadSafeImpulseConsole;
import org.rascalmpl.interpreter.Evaluator;

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
			PrintWriter debugStream = new PrintWriter(ThreadSafeImpulseConsole.INSTANCE.getWriter());
			debugStream.println("Initializing evaluator for: " + name + "...");
            debugStream.flush();
            Evaluator eval = CoreBundleEvaluatorFactory.construct();
            for (String m : modules) {
            	eval.doImport(null, m);
            }
            debugStream.println("Finished initializing evaluator for: " + name);
            debugStream.flush();
            return eval;
		});
	}

}
