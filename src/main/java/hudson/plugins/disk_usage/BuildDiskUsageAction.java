package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildBadgeAction;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ProminentProjectAction;
import java.util.LinkedList;
import java.util.List;

/**
 * Disk usage information for a single build
 * @author dvrzalik
 */
//TODO really implementsProminentProjectAction???
public class BuildDiskUsageAction implements ProminentProjectAction, BuildBadgeAction {

    Long diskUsage;
    AbstractBuild build;

    public BuildDiskUsageAction(AbstractBuild build, long diskUsage) {
        this.diskUsage = diskUsage;
        this.build = build;
    }

        public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return Messages.DisplayName();
    }

    public String getUrlName() {
        return Messages.UrlName();
    }
    
    /**
     * @return Disk usage of the build (included child builds)
     */
    public Long getDiskUsage() {

        for (AbstractBuild child : getChildBuilds(build)) {
            BuildDiskUsageAction bdua = child.getAction(BuildDiskUsageAction.class);
            if (bdua != null) {
                diskUsage += bdua.diskUsage;
            }
        }
        return diskUsage;
    }
    
    /**
     * @return Buidls of nested projects (like MavenModuleBuilds and MatrixRuns)
     */
    private static List<AbstractBuild> getChildBuilds(AbstractBuild build) {
        List<AbstractBuild> result = new LinkedList<AbstractBuild>();
        Job project = build.getParent();

        if (project instanceof ItemGroup) {
            for (Object child : ((ItemGroup) project).getItems()) {
                if (child instanceof AbstractProject) {
                    AbstractBuild childBuild = (AbstractBuild) ((AbstractProject) child).getNearestBuild(build.getNumber());
                    AbstractBuild nextBuild = (AbstractBuild) build.getNextBuild();
                    Integer nextBuildNumber = (nextBuild != null) ? nextBuild.getNumber() : Integer.MAX_VALUE;
                    while ((childBuild != null) && (childBuild.getNumber() < nextBuildNumber)) {
                        result.add(childBuild);
                        childBuild = (AbstractBuild) childBuild.getNextBuild();
                    }
                }
            }
        }

        return result;
    }
}
