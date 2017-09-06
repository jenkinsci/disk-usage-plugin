package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.disk_usage.project.DiskUsagePostBuildCalculation;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Build listener for calculation build disk usage
 * 
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageBuildListener extends RunListener<AbstractBuild>{
    
    @Override
    public void onCompleted(AbstractBuild build, TaskListener listener){
        Long diskUsage = build.getAction(BuildDiskUsageAction.class).getDiskUsage();
            if(build.getProject().getPublishersList().get(DiskUsagePostBuildCalculation.class)==null || diskUsage==0){
                DiskUsageUtil.calculationDiskUsageOfBuild(build, listener);
                try {
                    DiskUsageUtil.calculateWorkspaceDiskUsage(build.getProject());
                } catch (Exception ex) {
                    Logger.getLogger(DiskUsageBuildListener.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else{
                listener.getLogger().println("Skipping calculation of disk usage, it was already done in post build step.");
            }
            DiskUsageJenkinsAction.getInstance().actualizeCashedBuildsData();
            DiskUsageJenkinsAction.getInstance().actualizeCashedWorkspaceData();
            DiskUsageJenkinsAction.getInstance().actualizeCashedNotCustomWorkspaceData();
        }
    
    @Override
    public void onDeleted(AbstractBuild build){
        DiskUsageProperty property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
        if(property==null){
            try {
                property = DiskUsageUtil.addProperty(build.getProject());
            }
            catch(Exception e){
                Logger.getLogger(this.getClass().getName()).log(Level.WARNING, null, e);
                //try to find file and remove without property, until cause of the issue JENKINS-29147 is found
                ProjectDiskUsage diskUsage = new ProjectDiskUsage();
                diskUsage.setProject(build.getProject());
                if(diskUsage.getConfigFile().exists()) {
                    diskUsage.load();
                    diskUsage.removeBuild(new DiskUsageBuildInformation(build.getId(),build.getTimeInMillis(), build.number, 0l));
                    diskUsage.save();
                }
            }
        }
        DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getId());
        if(information!=null){;
            property.getDiskUsage().removeBuild(information);
            property.getDiskUsage().save();
        }
        DiskUsageJenkinsAction.getInstance().actualizeCashedBuildsData();
    }
    
    @Override
    public void onStarted(AbstractBuild build, TaskListener listener){
        DiskUsageProperty property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
        if(property==null){
            try {
                property = DiskUsageUtil.addProperty(build.getProject());
            }
            catch(Exception e){
                Logger.getLogger(this.getClass().getName()).log(Level.WARNING, null, e);
                //try to find file and add without property, until cause of the issue JENKINS-29147 is found
                ProjectDiskUsage diskUsage = new ProjectDiskUsage();
                diskUsage.setProject(build.getProject());
                if(diskUsage.getConfigFile().exists()) {
                    diskUsage.load();
                    diskUsage.addBuild(build);
                    diskUsage.save();
                }
            }
        }
        DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getId());
        if(information==null){
            property.getDiskUsage().addBuildInformation(new DiskUsageBuildInformation(build.getId(),build.getTimeInMillis(), build.getNumber(), 0l, build.isKeepLog()), build);
        }
    }

}
