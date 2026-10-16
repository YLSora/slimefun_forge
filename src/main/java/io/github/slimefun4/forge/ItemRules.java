package io.github.slimefun4.forge;

import java.math.*;
import java.util.*;

/** Internal numeric rules shared by item consumers. */
public final class ItemRules {
    private static BigDecimal decimal(float value){return new BigDecimal(Float.toString(value));}
    public static float charge(float value,float capacity){
        if(!Float.isFinite(capacity)||capacity<=0||(double)capacity>Integer.MAX_VALUE||decimal(capacity).setScale(2,RoundingMode.HALF_UP).floatValue()!=capacity)throw new IllegalArgumentException("Item capacity must be positive whole hundredths within Forge's integer range");
        if(!Float.isFinite(value)||value<0||value>capacity)throw new IllegalArgumentException("Item charge is outside capacity");
        return Math.min(capacity,decimal(value).setScale(2,RoundingMode.HALF_UP).floatValue());
    }
    private static void amount(float amount){if(!Float.isFinite(amount)||amount<=0)throw new IllegalArgumentException("Charge amount must be positive and finite");}
    public static float addCharge(float before,float amount,float capacity){amount(amount);charge(before,capacity);return charge(decimal(before).add(decimal(amount)).min(decimal(capacity)).floatValue(),capacity);}
    public static float subtractCharge(float before,float amount,float capacity){amount(amount);charge(before,capacity);return charge(decimal(before).subtract(decimal(amount)).floatValue(),capacity);}
    /** FE must report the exact whole-joule delta that the float-backed item can retain. */
    public static int receiveCharge(float before,int requested,float capacity){
        charge(before,capacity);int amount=Math.min(Math.max(0,requested),decimal(capacity).subtract(decimal(before)).intValue());
        if(amount==0)return 0;
        var delta=decimal(addCharge(before,amount,capacity)).subtract(decimal(before)).stripTrailingZeros();
        return delta.signum()>0&&delta.compareTo(BigDecimal.valueOf(amount))<=0&&delta.scale()<=0?delta.intValueExact():0;
    }
    static boolean fullSuit(List<String> sets){return sets.size()==4&&sets.get(0)!=null&&sets.stream().allMatch(sets.get(0)::equals);}
    static int exposure(int before,int radiation,int count){
        if(before<0||before>100||radiation<0||radiation>100||count<0)throw new IllegalArgumentException("Invalid radiation exposure");
        return (int)Math.min(100,before+(long)radiation*count);
    }
    private ItemRules(){}
}
