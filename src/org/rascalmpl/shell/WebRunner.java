package org.rascalmpl.shell;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;

import org.rascalmpl.runtime.RascalExecutionContext;

/**
 * Experimental runner for Rascal modules compiled to Java, meant to run in the browser under CheerpJ.
 *
 * From JavaScript (CheerpJ library mode, with the compiled program next to rascal.jar on the class path):
 * <pre>
 *   const lib = await cheerpjRunLibrary("/app/rascal.jar:/app/demo.jar");
 *   const WebRunner = await lib.org.rascalmpl.shell.WebRunner;
 *   const output = await WebRunner.run("rascal.$Demo");
 * </pre>
 */
public class WebRunner {

    /**
     * Instantiates the compiled module `className`, calls its `main()` function and
     * returns everything it printed, followed by its result and timing information.
     */
    public static String run(String className) {
        var buffer = new StringWriter();
        var out = new PrintWriter(buffer, true);
        try {
            long start = System.currentTimeMillis();
            var clazz = Class.forName(className);
            var rex = new RascalExecutionContext(Reader.nullReader(), out, out, null, null, clazz);
            var instance = clazz.getConstructor(RascalExecutionContext.class).newInstance(rex);
            long init = System.currentTimeMillis();
            var result = clazz.getMethod("main").invoke(instance);
            long end = System.currentTimeMillis();
            if (result != null) {
                out.println(result);
            }
            out.println("[init: " + (init - start) + " ms, exec: " + (end - init) + " ms]");
        }
        catch (InvocationTargetException e) {
            e.getCause().printStackTrace(out);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            e.printStackTrace(out);
        }
        out.flush();
        return buffer.toString();
    }

    public static void main(String[] args) {
        System.out.print(run(args[0]));
    }
}
