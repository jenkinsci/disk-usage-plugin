package hudson.plugins.disk_usage;

import hudson.model.BuildBadgeAction;

/**
 * Disk usage information for a single build
 * @author dvrzalik
 */
public class BuildDiskUsageAction extends DiskUsageAction implements BuildBadgeAction {
    
    long buildUsage;
    long allBuildsUsage;
    long wsUsage;
    
    public BuildDiskUsageAction(long wsUsage, long buildUsage, long allBuildsUsage) {
        this.wsUsage = wsUsage;
        this.buildUsage = buildUsage;
        this.allBuildsUsage = allBuildsUsage;
    }

    public long getBuildUsage() {
        return buildUsage;
    }

    public long getWsUsage() {
        return wsUsage;
    }

    public long getAllBuildsUsage() {
        return allBuildsUsage;
    }
    
    public String getBuildUsageString() {
        return getSizeString(buildUsage);
    }

    public String getWsUsageString() {
        return getSizeString(wsUsage);
    }

}
