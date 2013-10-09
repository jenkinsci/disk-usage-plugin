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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import jenkins.model.Jenkins;
import org.jfree.data.category.DefaultCategoryDataset;

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
        return getBuildsDiskUsage().get("all") + getDiskUsageWithoutBuilds();
    }
    
    private Map<String,Long> getBuildsDiskUsageAllSubItems(ItemGroup group, Date older, Date yonger){
        Map<String,Long> diskUsage = new TreeMap<String,Long>();
        Long buildsDiskUsage = 0l;
        Long locked = 0l;
        for(Object item: group.getItems()){
            if(item instanceof ItemGroup){
               ItemGroup subGroup = (ItemGroup) item;
               buildsDiskUsage += getBuildsDiskUsageAllSubItems(subGroup, older, yonger).get("all");
               locked += getBuildsDiskUsageAllSubItems(subGroup, older, yonger).get("locked");
            }
            if(item instanceof AbstractProject){
                AbstractProject p = (AbstractProject) item;
                List<AbstractBuild> builds = p.getBuilds();
                for(AbstractBuild build : builds){
                    BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
                    if(older!=null && !build.getTimestamp().getTime().before(older))
                        continue;
                    if(yonger!=null && !build.getTimestamp().getTime().after(yonger))
                        continue;
                    if (action != null) {
                        buildsDiskUsage += action.getDiskUsage();
                        if(build.isKeepLog())
                            locked += action.getDiskUsage();
                    } 
                }
            }
        }
        diskUsage.put("all", buildsDiskUsage);
        diskUsage.put("locked", locked);
        return diskUsage;
    }
    
    public Map<String, Long> getBuildsDiskUsage() {
        return getBuildsDiskUsage(null, null);
    }
    
    public Long getAllBuildsDiskUsage(){
        return getBuildsDiskUsage(null, null).get("all");
    }
    
    /**
     * @return Disk usage for all builds
     */
    public Map<String, Long> getBuildsDiskUsage(Date older, Date yonger) {
        Map<String,Long> diskUsage = new TreeMap<String,Long>();
        Long buildsDiskUsage = 0l;
        Long locked = 0l;
        if (project != null) {
            for(AbstractBuild build: project.getBuilds()){
                if(older!=null && !build.getTimestamp().getTime().before(older))
                    continue;
                if(yonger!=null && !build.getTimestamp().getTime().after(yonger))
                    continue;
                 BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
                 if (action != null) {
                    buildsDiskUsage += action.getDiskUsage();
                    if(build.isKeepLog())
                        locked += action.getDiskUsage();
                 }
            }  
            if(project instanceof ItemGroup){
               ItemGroup group = (ItemGroup) project;
               buildsDiskUsage += getBuildsDiskUsageAllSubItems(group, older, yonger).get("all");
               locked += getBuildsDiskUsageAllSubItems(group,older, yonger).get("locked");
            }
        }
        diskUsage.put("all", buildsDiskUsage);
        diskUsage.put("locked", locked);
        return diskUsage;
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
        DataSetBuilder<String, NumberOnlyBuildLabel> dsb2 = new DataSetBuilder<String, NumberOnlyBuildLabel>();
        List<Object[]> usages = new ArrayList<Object[]>();
        long maxValue = 0;
        //First iteration just to get scale of the y-axis
        for (AbstractBuild build : project.getBuilds()) {
            BuildDiskUsageAction dua = build.getAction(BuildDiskUsageAction.class);
            if (dua != null) {
                maxValue = Math.max(maxValue, getJobRootDirDiskUsage());
                usages.add(new Object[]{build, getJobRootDirDiskUsage(), dua.getAllDiskUsage(), getAllDiskUsageWorkspace()});
            }
        }

        int floor = (int) DiskUsageUtil.getScale(maxValue);
        String unit = DiskUsageUtil.getUnitString(floor);
        double base = Math.pow(1024, floor);
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Object[] usage : usages) {
            NumberOnlyBuildLabel label = new NumberOnlyBuildLabel((AbstractBuild) usage[0]);
            dataset.addValue(((Long) usage[1]) / base, "job directory", label);  
            dataset.addValue(((Long) usage[2]) / base, "build direcotory", label);     
            dsb2.add(((Long) usage[3]) / base, "all jobs workspaces", label);
        }
        return new DiskUsageGraph(dataset, unit, dsb2.build());   
    }

    /** Shortcut for the jelly view */
    public boolean showGraph() {
        return Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().isShowGraph();
    }
}
