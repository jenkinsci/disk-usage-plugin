package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildBadgeAction;
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.util.LinkedList;
import java.util.List;

/**
 * Disk usage information for a single build
 * @author dvrzalik
 */
public class BuildDiskUsageAction extends DiskUsageAction implements BuildBadgeAction {

    DiskUsage diskUsage;
    AbstractBuild build;

    public BuildDiskUsageAction(AbstractBuild build, long wsUsage, long buildUsage) {
        diskUsage = new DiskUsage(buildUsage, wsUsage);
        this.build = build;
    }

    /**
     * @return Disk usage of the build (included child builds)
     */
    public DiskUsage getDiskUsage() {
        DiskUsage du = (diskUsage != null) ? 
            new DiskUsage(diskUsage.buildUsage, diskUsage.wsUsage) :
            new DiskUsage(0,0);

        for (AbstractBuild child : getChildBuilds(build)) {
            BuildDiskUsageAction bdua = child.getAction(BuildDiskUsageAction.class);
            if (bdua != null) {
                du.buildUsage += bdua.diskUsage.getBuildUsage();
            }
        }
        
        //In case there is no workspace size available, refer to the previous result
        AbstractBuild previous = build;
        while((du.wsUsage == 0) && 
                ((previous = (AbstractBuild) previous.getPreviousBuild()) != null)) {
            BuildDiskUsageAction bdua = previous.getAction(BuildDiskUsageAction.class);    
            if (bdua != null) {
                du.wsUsage = bdua.diskUsage.wsUsage;
            }
        }
        return du;
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
