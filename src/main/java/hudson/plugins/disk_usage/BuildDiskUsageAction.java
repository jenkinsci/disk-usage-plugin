package hudson.plugins.disk_usage;

import hudson.FilePath;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import java.io.File;
import java.io.IOException;
import java.util.Map;
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
        //DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getId());
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
        ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
        if(action!=null){
            action.actualizeCashedBuildsData();
        }
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
                if(property!=null && (project instanceof TopLevelItem)){
                    FilePath workspace = build.getWorkspace();
                    if(workspace==null){
                        workspace = node.getWorkspaceFor((TopLevelItem)project);
                    }
                    Map<String,Long> paths = property.getSlaveWorkspaceUsage().get(node.getDisplayName());
                    Long size = null;
                    if(paths!=null){
                        size = paths.get(workspace.getRemote());
                    }
                    try {
                        //previous data about workspace was quite tricky, so check if there is such workspace and size were not recounted
                        if(workspace.exists() && size!=null && size>0){
                            property.putSlaveWorkspaceSize(node, node.getWorkspaceFor((TopLevelItem)project).getRemote(), diskUsage.wsUsage);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(BuildDiskUsageAction.class.getName()).log(Level.WARNING, null, ex);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(BuildDiskUsageAction.class.getName()).log(Level.WARNING, null, ex);
                    }
                }
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
        if(buildDiskUsage != null){
            size = buildDiskUsage;
        }
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
            DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getNumber());
            //Transient actions can be created even during deletion of job
            Boolean isLocked = DiskUsageUtil.isKeepLog(build);
            if(information==null && build.getRootDir().exists()){
                if(isLocked==null){
                    isLocked=false;
                }
                property.getDiskUsage().addBuildInformation(new DiskUsageBuildInformation(build.getId(),build.getTimeInMillis(), build.getNumber(), size, isLocked), build);
            }
            else{
                if(information!=null && !(build.getProject() instanceof ItemGroup)){
                    //check if lock is still valide
                    //not for ItemGroup, because MatrixProject causes recursion
                    
                    if(isLocked!=null && information.isLocked()!= isLocked){
                       information.setLockState(isLocked);
                       property.getDiskUsage().save();
                    }
                }
                
            }
            
            if(action!=null || buildDiskUsage!=null){
                property.getDiskUsageBuildInformation(build.getNumber()).setSize(buildDiskUsage);
                buildDiskUsage=null;
                //remove old action, now it is added by transition action factor
                build.getActions().remove(action);
                try {
                    build.save();
                } catch (IOException ex) {
                    Logger.getLogger(BuildDiskUsageAction.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
    }
       
}
