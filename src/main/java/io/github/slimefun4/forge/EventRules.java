package io.github.slimefun4.forge;

import java.util.*;

/** Shared event validation and commit cleanup; no Minecraft objects or world access. */
public final class EventRules {
    /** Native protection has already finished; stop core dispatch as soon as one handler consumes it. */
    public static void dispatch(java.util.function.BooleanSupplier cancelled,Runnable...actions){for(var action:actions){if(cancelled.getAsBoolean())return;action.run();}}
    public static void velocity(double x,double y,double z){if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||x*x+y*y+z*z>16)throw new IllegalArgumentException("Launch velocity must be finite and at most 4 blocks/tick");}
    public static <T> List<T> targets(Collection<T> targets,Set<T> allowed){var result=new LinkedHashSet<T>();for(var target:targets){if(target==null||!allowed.contains(target))throw new IllegalArgumentException("Target outside the original selection");result.add(target);}return List.copyOf(result);}
    public static void stack(int count,int maximum){if(count<1||count>maximum)throw new IllegalArgumentException("Invalid event stack size");}
    public static void position(double x,double y,double z){if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30000000||Math.abs(z)>30000000)throw new IllegalArgumentException("Invalid item spawn position");}
    /** RC-37 uses roll > chance for rejection, including the inclusive lower boundary. */
    public static boolean chance(int roll,int chance){if(roll<0||roll>=100||chance<0||chance>100)throw new IllegalArgumentException("Invalid talisman chance");return roll<=chance;}
    public static long exclusive(long elapsed,long children){if(elapsed<0||children<0||children>elapsed)throw new IllegalArgumentException("Invalid nested timing");return elapsed-children;}
    /** An unsuccessful or exceptional join must not leave a deliverable entity beside its retained source. */
    public static boolean commitSpawn(java.util.function.BooleanSupplier sourceValid,java.util.function.BooleanSupplier join,Runnable discard){
        boolean committed=false;Throwable failure=null;
        try{committed=sourceValid.getAsBoolean()&&join.getAsBoolean()&&sourceValid.getAsBoolean();return committed;}
        catch(RuntimeException|Error error){failure=error;throw error;}
        finally{if(!committed)try{discard.run();}catch(RuntimeException|Error cleanup){if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;}}
    }
    private EventRules(){}
}
