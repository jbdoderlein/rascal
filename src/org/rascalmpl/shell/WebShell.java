package org.rascalmpl.shell;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.rascalmpl.debug.NullRascalMonitor;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.staticErrors.StaticError;
import org.rascalmpl.interpreter.utils.ReadEvalPrintDialogMessages;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.io.StandardTextWriter;
import io.usethesource.vallang.type.TypeFactory;

/**
 * Experimental, terminal-free Rascal shell, meant to run in the browser under CheerpJ.
 *
 * From JavaScript (CheerpJ library mode):
 * <pre>
 *   const lib = await cheerpjRunLibrary("/app/rascal.jar");
 *   const WebShell = await lib.org.rascalmpl.shell.WebShell;
 *   const shell = await new WebShell();
 *   const output = await shell.eval("1 + 1;");
 * </pre>
 *
 * Or as a main class, which evaluates every line read from stdin.
 */
public class WebShell {
    private static final ISourceLocation PROMPT = URIUtil.rootLocation("prompt");

    private final StringWriter buffer = new StringWriter();
    private final PrintWriter out = new PrintWriter(buffer, true);
    private final StandardTextWriter printer = new StandardTextWriter(true);
    private final Evaluator eval;

    public WebShell() {
        eval = ShellEvaluatorFactory.getBasicEvaluator(Reader.nullReader(), out, out, new NullRascalMonitor());
    }

    /**
     * Adds a source folder to the module search path, e.g. "resource:///" for modules packed
     * in a jar on the class path.
     */
    public void addSearchPath(String uri) throws URISyntaxException {
        eval.addRascalSearchPath(URIUtil.createFromURI(uri));
    }

    /**
     * Calls a Rascal function without parameters (after `eval("import ...;")`), and returns its
     * result: the string itself for a `str`, otherwise its printed value. Returns null when the
     * call fails; then {@link #lastError()} says why.
     */
    public synchronized String call0(String function) {
        return call(function);
    }

    /**
     * Calls a Rascal function with one `str` parameter, e.g. to pass JSON back and forth.
     * Returns null when the call fails; then {@link #lastError()} says why.
     */
    public synchronized String call1(String function, String arg) {
        return call(function, eval.getValueFactory().string(arg));
    }

    /**
     * The Rascal error message or Java stack trace of the last failed call. (Java exceptions reach
     * JavaScript as opaque objects under CheerpJ, so the calls return null and keep the text here.)
     */
    public synchronized String lastError() {
        return lastError;
    }

    private String lastError = null;

    private String call(String function, IValue... args) {
        lastError = null;
        try {
            var result = eval.call(function, args);
            return result instanceof IString ? ((IString) result).getValue() : String.valueOf(result);
        }
        catch (ParseError | StaticError | Throw e) {
            var message = new StringWriter();
            ReadEvalPrintDialogMessages.parseOrStaticOrThrowMessage(new PrintWriter(message), e, printer);
            lastError = message.toString();
        }
        catch (Throwable e) {
            // under CheerpJ, Java exceptions have no stack trace; the Rascal one says where it happened
            var trace = new StringWriter();
            var current = eval.getCurrentAST();
            ReadEvalPrintDialogMessages.throwableMessage(new PrintWriter(trace), e, eval.getStackTrace(), printer);
            lastError = (current != null ? "while evaluating " + current.getLocation() + ":\n" : "") + trace;
        }
        System.err.println("WebShell: calling " + function + " failed:\n" + lastError);
        return null;
    }

    /**
     * Evaluates a command (or declaration) and returns everything it printed, followed by its result.
     */
    public synchronized String eval(String command) {
        try {
            var result = eval.eval(eval.getMonitor(), command, PROMPT);
            if (result != null && !result.getStaticType().isBottom() && !result.getStaticType().equivalent(TypeFactory.getInstance().voidType())) {
                out.print(result.getStaticType());
                out.print(": ");
                printer.write(result.getValue(), out);
                out.println();
            }
            else {
                out.println("ok");
            }
        }
        catch (ParseError | StaticError | Throw e) {
            ReadEvalPrintDialogMessages.parseOrStaticOrThrowMessage(out, e, printer);
        }
        catch (Throwable e) {
            ReadEvalPrintDialogMessages.throwableMessage(out, e, eval.getStackTrace(), printer);
        }
        out.flush();
        var output = buffer.toString();
        buffer.getBuffer().setLength(0);
        return output;
    }

    /**
     * Evaluates every line on stdin; the arguments are extra search path locations.
     */
    public static void main(String[] args) throws Exception {
        var shell = new WebShell();
        for (var path : args) {
            shell.addSearchPath(path);
        }
        var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.print("rascal> ");
        System.out.flush();
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.isBlank()) {
                System.out.print(shell.eval(line));
            }
            System.out.print("rascal> ");
            System.out.flush();
        }
    }
}
