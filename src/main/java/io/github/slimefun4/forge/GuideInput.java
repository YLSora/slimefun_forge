package io.github.slimefun4.forge;

import java.util.Locale;

final class GuideInput {
    static String normalize(String query){
        if(query.length()>256)throw new IllegalArgumentException("Guide search exceeds 256 characters");
        return query.replaceAll("(?i)[&§][0-9a-fk-orx]","").strip().toLowerCase(Locale.ROOT);
    }
    static boolean expired(long start,long now){return now-start>=1200;}
    private GuideInput(){}
}
