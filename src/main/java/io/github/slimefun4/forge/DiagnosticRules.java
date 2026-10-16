package io.github.slimefun4.forge;

import java.util.Objects;
import java.util.function.Consumer;

/** Report recoverable callbacks without swallowing fatal errors or replacing the business cause. */
public final class DiagnosticRules {
    public static long increment(long count){return count==Long.MAX_VALUE?count:count+1;}
    public static boolean execute(Runnable action,Consumer<RuntimeException> report){
        Objects.requireNonNull(action);Objects.requireNonNull(report);
        try{action.run();return true;}
        catch(RuntimeException error){try{report.accept(error);}catch(RuntimeException|Error reporting){if(error!=reporting)error.addSuppressed(reporting);throw error;}return false;}
    }
    private DiagnosticRules(){}
}
