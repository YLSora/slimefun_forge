package io.github.slimefun4.forge;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Internal production rules for player state, issued inventory identities and robot programs. */
public final class PlayerRules {
    public static long deadline(long now,long duration,TimeUnit unit){
        if(now<=0||duration<0)throw new IllegalArgumentException("Invalid wall clock/duration");
        long millis=Objects.requireNonNull(unit).toMillis(duration);
        if(duration>0&&millis==0||millis==Long.MAX_VALUE||millis>Long.MAX_VALUE-now)throw new IllegalArgumentException("Status duration is sub-millisecond or overflows");return now+millis;
    }
    public static boolean present(long deadline,long now){return deadline==0||deadline>=now;}
    public static int nextId(int last){if(last< -1||last==Integer.MAX_VALUE)throw new IllegalArgumentException("Backpack IDs exhausted");return last+1;}
    public static int slots(int rows){if(rows<1||rows>6)throw new IllegalArgumentException("Backpack rows must be 1..6");return rows*9;}
    public static List<String> program(List<String> instructions,Set<String> allowed){
        var code=List.copyOf(instructions);if(code.size()<2||code.size()>54||!code.get(0).endsWith(":start")||!code.get(code.size()-1).endsWith(":repeat"))throw new IllegalArgumentException("Invalid robot program boundaries/size");
        for(int i=0;i<code.size();i++){var instruction=code.get(i);if(!allowed.contains(instruction)||i>0&&i<code.size()-1&&(instruction.endsWith(":start")||instruction.endsWith(":repeat")))throw new IllegalArgumentException("Unsupported robot instruction "+instruction);}return code;
    }
    public static int nextInstruction(int index,int size){if(size<2||index<0||index>=size)throw new IllegalArgumentException("Invalid robot instruction position");return index+1==size?0:index+1;}
    private PlayerRules(){}
}
