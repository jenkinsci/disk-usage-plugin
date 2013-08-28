package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.ItemGroup;
import hudson.model.ProminentProjectAction;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

/**
 * Disk usage of a project
 * 
 * @author dvrzalik
 */
public class ProjectDiskUsageAction implements ProminentProjectAction {

    AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project;
    
    public ProjectDiskUsageAction(AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project) {
        this.project = project;       
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
    
    public Long getDiskUsageWorkspace(){
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null)
            return 0l;
        return property.getAllWorkspaceSize();
    }
    
    /**
     * Returns all workspace disku usage including workspace usage its sub-projects
     * 
     * @return disk usage project and its sub-projects
     */
    public Long getAllDiskUsageWorkspace(){
        Long diskUsage = 0l;
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property!=null){
            diskUsage += property.getAllWorkspaceSize();
        }
        if(project instanceof ItemGroup){
            ItemGroup group = (ItemGroup) project;
            for(Object i:group.getItems()){
                if(i instanceof AbstractProject){
                    AbstractProject p = (AbstractProject) i;
                    DiskUsageProperty prop = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                    if(prop!=null){
                        diskUsage += prop.getAllWorkspaceSize();
                    } 
                }
            }
        }
        return diskUsage;
    }
    
    public Long getDiskUsageWithoutBuilds(){
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null)
            return 0l;
        return property.getDiskUsageWithoutBuilds();
    }
    
    public Long getAllDiskUsageWithoutBuilds(){
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null)
            return 0l;
        return property.getAllDiskUsageWithoutBuilds();
    }
    
    
    public Long getJobRootDirDiskUsage(){
        return getBuildsDiskUsage() + getDiskUsageWithoutBuilds();
    }
    
    private Long getBuildsDiskUsageAllSubItems(ItemGroup group){
        Long buildsDiskUsage = 0l;
        for(Object item: group.getItems()){
            if(item instanceof ItemGroup){
               ItemGroup subGroup = (ItemGroup) item;
               buildsDiskUsage += getBuildsDiskUsageAllSubItems(subGroup);
            }
            if(item instanceof AbstractProject){
                AbstractProject p = (AbstractProject) item;
                List<AbstractBuild> builds = p.getBuilds();
                for(AbstractBuild build : builds){
                   // System.out.println(build.getDisplayName());
                   BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
                    if (action != null) {
                        buildsDiskUsage += action.diskUsage;
                    } 
                }
            }
        }
        return buildsDiskUsage;
    }
    
    /**
     * @return Disk usage for all builds
     */
    public Long getBuildsDiskUsage() {
        Long buildsDiskUsage = 0l;
        if (project != null) {
            for(AbstractBuild build: project.getBuilds()){
                //System.out.println(build.getDisplayName());
                 BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
                 if (action != null) {
                    buildsDiskUsage += action.diskUsage;
                 }
            }  
            if(project instanceof ItemGroup){
               ItemGroup group = (ItemGroup) project;
               Long sub = getBuildsDiskUsageAllSubItems(group);
               buildsDiskUsage += sub;
            }
        }
        return buildsDiskUsage;
    }
    
    public BuildDiskUsageAction getLastBuildAction() {
        Run run = project.getLastBuild();
        if (run != null) {
            return run.getAction(BuildDiskUsageAction.class);
        }

        return null;
    }

    /**
     * Generates a graph with disk usage trend
     * 
     */
    public Graph getGraph() throws IOException {
        //TODO if(nothing_changed) return;

        DataSetBuilder<String, NumberOnlyBuildLabel> dsb = new DataSetBuilder<String, NumberOnlyBuildLabel>();

        List<Object[]> usages = new ArrayList<Object[]>();
        long maxValue = 0;
        //First iteration just to get scale of the y-axis
        for (AbstractBuild build : project.getBuilds()) {
            BuildDiskUsageAction dua = build.getAction(BuildDiskUsageAction.class);
            if (dua != null) {
                //maxValue = Math.max(maxValue, Math.max(dua.getDiskUsage());
                usages.add(new Object[]{build, dua.getDiskUsage()});
            }
        }

        int floor = (int) DiskUsageUtil.getScale(maxValue);
        String unit = DiskUsageUtil.getUnitString(floor);
        double base = Math.pow(1024, floor);

        for (Object[] usage : usages) {
            NumberOnlyBuildLabel label = new NumberOnlyBuildLabel((AbstractBuild) usage[0]);
            dsb.add(((Long) usage[2]) / base, "build", label);
        }

		return new DiskUsageGraph(dsb.build(), unit);
    }

    /** Shortcut for the jelly view */
    public boolean showGraph() {
        return Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).isShowGraph();
    }
}
