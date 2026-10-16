package io.github.slimefun4.forge;

/** Numeric equipment rules shared by native core and addon consumers. */
public final class EquipmentRules {
    public static void thrust(double value){if(!Double.isFinite(value)||value<0||value>4)throw new IllegalArgumentException("Flight thrust must be finite and within 0..4");}
    public static boolean sunlit(long dayTime){long time=Math.floorMod(dayTime,24000);return time<12300||time>23850;}
    private EquipmentRules(){}
}
