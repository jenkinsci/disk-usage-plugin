package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Disk usage information for a single build
 * @author dvrzalik
 */
//TODO really implementsProminentProjectAction???
@ExportedBean(defaultVisibility = 1)
public class BuildDiskUsageAction implements ProminentProjectAction, BuildBadgeAction, RunAction2 {

    @Deprecated
    Long buildDiskUsage;
    AbstractBuild build;
    @Deprecated
    DiskUsage diskUsage;
    
    public BuildDiskUsageAction(AbstractBuild build) {
        this.build = build;       
        DiskUsageProperty property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
        if(property==null){
            DiskUsageUtil.addProperty(build.getProject());
            property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
        }
        DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getId());
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
    
    public void setDiskUsage(Long size) throws IOException{
        AbstractProject project = build.getProject();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property==null){
            DiskUsageUtil.addProperty(project);
            property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        }
        DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getId());
        if(information!=null){
            information.setSize(size);
        }
        else{    
            property.getDiskUsage().addBuildInformation(new DiskUsageBuildInformation(build.getId(), build.getTimeInMillis(), build.getNumber(), size, build.isKeepLog()), build);
        }
        property.saveDiskUsage(); 
    }
    
    /**
     * @return Disk usage of the build (included child builds)
     */
    public Long getDiskUsage() {
        AbstractProject project = build.getProject();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property==null){
            DiskUsageUtil.addProperty(project);
            property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        }
        return property.getDiskUsageOfBuild(build.getId());
    }
    
    public Long getAllDiskUsage(){
        Long buildsDiskUsage = getDiskUsage();
        AbstractProject project = build.getProject();
        if(project instanceof ItemGroup){
           buildsDiskUsage += getBuildsDiskUsageAllSubItems((ItemGroup)project);
        }       
        return buildsDiskUsage;
    }
    
    public String getBuildUsageString(){
        return DiskUsageUtil.getSizeString(getAllDiskUsage());
    }

    private Long getBuildsDiskUsageAllSubItems(ItemGroup group){
        Long buildsDiskUsage = 0l;
        for(Object item: group.getItems()){
            if(item instanceof ItemGroup){
                buildsDiskUsage += getBuildsDiskUsageAllSubItems((ItemGroup)item);
            }
            else{
                if(item instanceof AbstractProject){
                    AbstractProject project = (AbstractProject) item;
                    DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
                    if(property==null){
                        DiskUsageUtil.addProperty(project);
                        property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
                    }
                    Set<DiskUsageBuildInformation> informations = property.getDiskUsageOfBuilds();
                    for(DiskUsageBuildInformation information :  informations){
                        if(information.getNumber() == build.getNumber()){
                            buildsDiskUsage += information.getSize();
                        }
                    }                
                }
            }
        }
        return buildsDiskUsage;
    }
    
    public Object readResolve() {
        //for keeping backward compatibility
        if(diskUsage!=null){
            buildDiskUsage = diskUsage.buildUsage;
            Node node = build.getBuiltOn();
            if(node!=null && diskUsage.wsUsage!=null && diskUsage.wsUsage > 0){
                DiskUsageProperty property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
                AbstractProject project = build.getProject().getRootProject();
                if(property!=null && (project instanceof TopLevelItem))
                    property.putSlaveWorkspaceSize(node, node.getWorkspaceFor((TopLevelItem)project).getRemote(), diskUsage.wsUsage);
            }
            diskUsage=null;
        }
        return this;
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        //no action is needed
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        DiskUsageProperty property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
        long size = 0L;
        if(property==null){
            return;
        }
        //backward compatibility
            BuildDiskUsageAction action = null;
            for(Action a : build.getActions()){
                if(a instanceof BuildDiskUsageAction){
                    action = (BuildDiskUsageAction) a;
                    if(action.buildDiskUsage != null){
                        size=action.buildDiskUsage;
                    }            
                }
            }
            if(action!=null){
                //remove old action, now it is added by transition action factory
                build.getActions().remove(action);
                try {
                    build.save();
                } catch (IOException ex) {
                    Logger.getLogger(BuildDiskUsageAction.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getNumber());
            //Transient actions can be created even during deletion of job
            if(information==null && build.getRootDir().exists()){
                property.getDiskUsage().addBuildInformation(new DiskUsageBuildInformation(build.getId(),build.getTimeInMillis(), build.getNumber(), size, build.isKeepLog()), build);
            }
            else{
                if(information!=null && !(build.getProject() instanceof ItemGroup)){
                    //check if lock is still valide
                    //not for ItemGroup, because MatrixProject causes recursion
                    if(information.isLocked()!= build.isKeepLog()){
                       information.setLockState(build.isKeepLog());
                       property.getDiskUsage().save();
                    }
                }
                
            }
    }
       
}
