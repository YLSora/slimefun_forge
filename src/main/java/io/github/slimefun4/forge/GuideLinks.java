package io.github.slimefun4.forge;

import java.net.URI;

/** Validate links before exposing them as native OPEN_URL components; never fetch them. */
public final class GuideLinks {
    public static String wiki(String link){
        if(link==null)return null;
        var uri=URI.create(link);
        if(!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))||uri.getHost()==null||uri.getRawUserInfo()!=null)throw new IllegalArgumentException("Wiki requires an absolute HTTP(S) URL without credentials");
        return uri.toASCIIString();
    }
    private GuideLinks(){}
}
