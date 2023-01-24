package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.disk_usage.project.DiskUsagePostBuildCalculation;

/**
 * Build listener for calculation build disk usage
 * 
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageBuildListener extends RunListener<AbstractBuild> {

    @Override
    public void onCompleted(AbstractBuild build, TaskListener listener) {
        Long diskUsage = build.getAction(BuildDiskUsageAction.class).getDiskUsage();
        if(build.getProject().getPublishersList().get(DiskUsagePostBuildCalculation.class) == null || diskUsage == 0) {
            DiskUsageUtil.calculationDiskUsageOfBuild(build, listener);
        }
        else {
            listener.getLogger().println("Skipping calculation of disk usage, it was already done in post build step.");
        }
    }

    @Override
    public void onDeleted(AbstractBuild build) {
        DiskUsageProperty property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
        if(property == null) {
            DiskUsageUtil.addProperty(build.getProject());
            property =  (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
        }
        DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getId());
        if(information != null) {
            property.getDiskUsage().removeBuild(information);
            property.getDiskUsage().save();
        }
    }

    @Override
    public void onStarted(AbstractBuild build, TaskListener listener) {
        DiskUsageProperty property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
        if(property == null) {
            DiskUsageUtil.addProperty(build.getProject());
            property =  (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
        }
        DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getId());
        if(information == null) {
            property.getDiskUsage().addBuildInformation(new DiskUsageBuildInformation(build.getId(), build.getTimeInMillis(), build.getNumber(), 0l), build);
        }
    }

}
