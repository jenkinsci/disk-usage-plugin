package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildBadgeAction;
import hudson.model.ItemGroup;
import hudson.model.ProminentProjectAction;

/**
 * Disk usage information for a single build
 * @author dvrzalik
 */
//TODO really implementsProminentProjectAction???
public class BuildDiskUsageAction implements ProminentProjectAction, BuildBadgeAction {

    Long buildDiskUsage;
    AbstractBuild build;
    @Deprecated
    DiskUsage diskUsage;

    public BuildDiskUsageAction(AbstractBuild build, long diskUsage) {
        this.buildDiskUsage = diskUsage;
        this.build = build;
    }
        
    public void setDiskUsage(Long diskUsage){
        this.buildDiskUsage=diskUsage;
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
        return buildDiskUsage;
    }
    
    public Long getAllDiskUsage(){
        Long buildsDiskUsage = buildDiskUsage;
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
            if(item instanceof AbstractProject){
                AbstractProject project = (AbstractProject) item;
                AbstractBuild b = (AbstractBuild) project.getBuildByNumber(build.getNumber());
                if(b!=null && b.getAction(BuildDiskUsageAction.class)!=null)
                    buildsDiskUsage += b.getAction(BuildDiskUsageAction.class).buildDiskUsage;
            }
        }
        return buildsDiskUsage;
    }
    
    public Object readResolve() {
        //for keeping backward compatibility
        if(diskUsage!=null){
            buildDiskUsage = diskUsage.buildUsage;
            diskUsage=null;
        }
        return this;
    }
       
}
