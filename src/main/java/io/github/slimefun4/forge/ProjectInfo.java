package io.github.slimefun4.forge;

import java.util.List;

/** Packaged attribution. */
public final class ProjectInfo {
    public record Contributor(String name,String role) {}
    static final List<Contributor> CONTRIBUTORS=List.of(GuideContent.read("/slimefun/contributors.json",Contributor[].class));
    private ProjectInfo(){}
}
