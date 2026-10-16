package io.github.slimefun4.forge;

/** Checked machine arithmetic shared by the public processor and native consumers. */
public final class MachineRules {
    public static int advance(int progress,int amount,int total){
        if(total<0||progress<0||progress>total||amount<0)throw new IllegalArgumentException("Invalid machine progress");
        return (int)Math.min(total,(long)progress+amount);
    }
    public static int bar(int progress,int total,int width){
        advance(progress,0,total);if(width<1||width>1000)throw new IllegalArgumentException("Progress width must be 1..1000");
        return total==0?width:(int)((long)progress*width/total);
    }
    public static int positive(int value){if(value<1)throw new IllegalArgumentException("Machine amount/duration must be positive");return value;}
    private MachineRules(){}
}
