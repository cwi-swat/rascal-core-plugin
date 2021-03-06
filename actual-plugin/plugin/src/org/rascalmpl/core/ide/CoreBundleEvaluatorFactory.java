package org.rascalmpl.core.ide;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.nature.ProjectEvaluatorFactory;
import org.rascalmpl.eclipse.util.ThreadSafeImpulseConsole;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.uri.URIUtil;

public class CoreBundleEvaluatorFactory {
	
	public static Evaluator construct() {
		Bundle coreBundle = findRascalCoreBundle();
		Evaluator eval = ProjectEvaluatorFactory.getInstance().getBundleEvaluator(coreBundle, ThreadSafeImpulseConsole.INSTANCE.getWriter(), ThreadSafeImpulseConsole.INSTANCE.getWriter());
		addNestedJarsToBundle(eval, coreBundle);
		return eval;
	}

    private static void addNestedJarsToBundle(Evaluator eval, Bundle coreBundle) {
    	//URIUtil.correctLocation("plugin", bundle.getSymbolicName();
    	Enumeration<URL> ents = coreBundle.findEntries("jars/", "*.jar", false);
    	while (ents.hasMoreElements()) {
    		URL nestedJar = ents.nextElement();
    		try {
				URL unpacked = FileLocator.toFileURL(nestedJar);
				String jarFile = new File(URIUtil.fromURL(unpacked)).getAbsolutePath();
				ProjectEvaluatorFactory.addJarToSearchPath(URIUtil.createFileLocation(jarFile), eval);
			} catch (IOException | URISyntaxException e) {
				Activator.log("Cannot load nested jar: " + nestedJar, e);
			}
    	}
	}

	private static Bundle findRascalCoreBundle() {
    	return Platform.getBundle("org.rascalmpl.rascal_core_bundle");
    }

}
