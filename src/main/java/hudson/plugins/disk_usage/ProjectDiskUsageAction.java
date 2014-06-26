package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.ItemGroup;
import hudson.model.ProminentProjectAction;
import hudson.util.Graph;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    public Long getAllSlaveWorkspaces(){
        return getAllDiskUsageWorkspace() - getAllCustomOrNonSlaveWorkspaces();
    }
    
    public Long getAllCustomOrNonSlaveWorkspaces(){
        Long diskUsage = 0l;
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property!=null){
            diskUsage += property.getAllNonSlaveOrCustomWorkspaceSize();
        }
        if(project instanceof ItemGroup){
            ItemGroup group = (ItemGroup) project;
            for(Object i:group.getItems()){
                if(i instanceof AbstractProject){
                    AbstractProject p = (AbstractProject) i;
                    DiskUsageProperty prop = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                    if(prop!=null){
                        diskUsage += prop.getAllNonSlaveOrCustomWorkspaceSize();
                    } 
                }
            }
        }
        return diskUsage;
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
    
    public String getSizeInString(Long size){
       return DiskUsageUtil.getSizeString(size);
    }
    
    public Long getDiskUsageWithoutBuilds(){
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null){
            property = new DiskUsageProperty();
            try {
                project.addProperty(property);
            } catch (IOException ex) {
                Logger.getLogger(ProjectDiskUsageAction.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return property.getDiskUsageWithoutBuilds();
    }
    
    public Long getAllDiskUsageWithoutBuilds(){
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null){
            return 0l;
        }
        return property.getAllDiskUsageWithoutBuilds();
    }
    
    
    public Long getJobRootDirDiskUsage() {
        try {
            return getBuildsDiskUsage().get("all") + getDiskUsageWithoutBuilds();
        } catch (IOException ex) {
            Logger.getLogger(ProjectDiskUsageAction.class.getName()).log(Level.SEVERE, null, ex);
            return 0L;
        }
    }
    
    private Map<String,Long> getBuildsDiskUsageAllSubItems(ItemGroup group, Date older, Date yonger) throws IOException{
        Map<String,Long> diskUsage = new TreeMap<String,Long>();
        Long buildsDiskUsage = 0l;
        Long locked = 0l;
        Long notLoaded = 0L;
        for(Object item: group.getItems()){
            if(item instanceof ItemGroup){
               ItemGroup subGroup = (ItemGroup) item;
               buildsDiskUsage += getBuildsDiskUsageAllSubItems(subGroup, older, yonger).get("all");
               locked += getBuildsDiskUsageAllSubItems(subGroup, older, yonger).get("locked");
               notLoaded += getBuildsDiskUsageAllSubItems(subGroup, older, yonger).get("notLoaded");
            }
            else{
                if(group instanceof AbstractProject){
                    AbstractProject p = (AbstractProject) item;
                    DiskUsageProperty property = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                    if(property==null){
                        property = new DiskUsageProperty();
                        p.addProperty(property);
                        property.loadDiskUsage();
                    }
                    Set<DiskUsageBuildInformation> informations = property.getDiskUsageOfBuilds();
                    for(DiskUsageBuildInformation information: informations){
                        Date date = null;
                        try {
                            date = Run.getIDFormatter().parse(information.getId());
                        } catch (ParseException ex) {
                            Logger.getLogger(ProjectDiskUsageAction.class.getName()).log(Level.SEVERE, null, ex);
                            continue;
                        }
                        if(older!=null && !date.before(older))
                            continue;
                        if(yonger!=null && !date.after(yonger))
                            continue;
                        Long size = information.getSize();
                        buildsDiskUsage += size;
                        Collection<AbstractBuild> loadedBuilds = (Collection<AbstractBuild>) p._getRuns().getLoadedBuilds().values();
                        AbstractBuild build = null;
                        for (AbstractBuild b : loadedBuilds){
                            if(b.getId().equals(information.getId())){
                                build = b;
                            }
                        }
                        if(build!=null){
                            if(build.isKeepLog()){
                                locked += size;
                            }
                        }
                        else{
                            notLoaded += size;
                        }
                    }
                }
            }
            
        }
        diskUsage.put("all", buildsDiskUsage);
        diskUsage.put("locked", locked);
        diskUsage.put("notLoaded", notLoaded);
        return diskUsage;
    }
    
    public Map<String, Long> getBuildsDiskUsage() throws IOException {
        return getBuildsDiskUsage(null, null);
    }
    
    public Long getAllBuildsDiskUsage() throws IOException{
        return getBuildsDiskUsage(null, null).get("all");
    }
    
    /**
     * @return Disk usage for all builds
     */
    public Map<String, Long> getBuildsDiskUsage(Date older, Date yonger) throws IOException {
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null){
            property = new DiskUsageProperty();
            project.addProperty(property);
            property.loadDiskUsage();
        }
        Map<String,Long> diskUsage = new TreeMap<String,Long>();
        Long buildsDiskUsage = 0l;
        Long locked = 0l;
        Long notLoaded = 0l;
        if (project != null) {
            if(project instanceof ItemGroup){
               ItemGroup group = (ItemGroup) project;
               Map<String,Long> sizes = getBuildsDiskUsageAllSubItems(group, older, yonger);
               buildsDiskUsage += sizes.get("all");
               locked += sizes.get("locked");
               notLoaded += sizes.get("notLoaded");
            }
          Set<DiskUsageBuildInformation> informations = property.getDiskUsageOfBuilds();
          for(DiskUsageBuildInformation information: informations){
            Date date = null;
                try {
                    date = Run.getIDFormatter().parse(information.getId());
                } catch (ParseException ex) {
                    Logger.getLogger(ProjectDiskUsageAction.class.getName()).log(Level.SEVERE, null, ex);
                    continue;
                }
            if(older!=null && !date.before(older))
                continue;
            if(yonger!=null && !date.after(yonger))
                continue;
            Long size = information.getSize();
            buildsDiskUsage += size;
            Collection<AbstractBuild> loadedBuilds = (Collection<AbstractBuild>) project._getRuns().getLoadedBuilds().values();
            AbstractBuild build = null;
            for (AbstractBuild b : loadedBuilds){
                if(b.getId().equals(information.getId())){
                    build = b;
                }
            }
            if(build!=null){
                if(build.isKeepLog()){
                    locked += size;
                }
            }
            else{
                notLoaded += size;
            }
          }
        }
        diskUsage.put("all", buildsDiskUsage);
        diskUsage.put("locked", locked);
        diskUsage.put("notLoaded", notLoaded);
        return diskUsage;
    }
    
    public BuildDiskUsageAction getLastBuildAction() {
        Run run = project.getLastBuild();
        if (run != null) {
            return run.getAction(BuildDiskUsageAction.class);
        }

        return null;
    }
    
    public Set<DiskUsageBuildInformation> getBuildsInformation() throws IOException{
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null){
            property = new DiskUsageProperty();
            project.addProperty(property);
        }
        return property.getDiskUsageOfBuilds();
    }

    /**
     * Generates a graph with disk usage trend
     * 
     */
    public Graph getGraph() throws IOException {
        //TODO if(nothing_changed) return;
        List<Object[]> usages = new ArrayList<Object[]>();
        long maxValue = 0;
        long maxValueWorkspace = 0;
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        maxValueWorkspace = Math.max(getAllCustomOrNonSlaveWorkspaces(), getAllSlaveWorkspaces());
        maxValue =  getJobRootDirDiskUsage();
        //First iteration just to get scale of the y-axis
        TreeSet<DiskUsageBuildInformation> builds = new TreeSet<DiskUsageBuildInformation>();
        builds.addAll(property.getDiskUsageOfBuilds());
        //do it in reverse order
        for (DiskUsageBuildInformation build : builds) {
            
           usages.add(new Object[]{build, getJobRootDirDiskUsage(), property.getAllDiskUsageOfBuild(build.getNumber()), getAllSlaveWorkspaces(), getAllCustomOrNonSlaveWorkspaces()});
            maxValue = Math.max(maxValue, build.getSize());
        }

        int floor = (int) DiskUsageUtil.getScale(maxValue);
        String unit = DiskUsageUtil.getUnitString(floor);
        int workspaceFloor = (int) DiskUsageUtil.getScale(maxValueWorkspace);
        String workspaceUnit = DiskUsageUtil.getUnitString(workspaceFloor);
        double base = Math.pow(1024, floor);
        double workspaceBase = Math.pow(1024, workspaceFloor);
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        DefaultCategoryDataset dataset2 = new DefaultCategoryDataset();
        for (Object[] usage : usages) {
            Integer label = ((DiskUsageBuildInformation) usage[0]).getNumber();
            dataset.addValue(((Long) usage[1]) / base, "job directory", label);  
            dataset.addValue(((Long) usage[2]) / base, "build directory", label);
            dataset2.addValue(((Long) usage[3]) / workspaceBase, "all slave workspaces of job", label);
            dataset2.addValue(((Long) usage[4]) / workspaceBase, "all non slave workspaces of job", label);
        }
        return new DiskUsageGraph(dataset, unit, dataset2, workspaceUnit);   
    }

    /** Shortcut for the jelly view */
    public boolean showGraph() {
        return Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().isShowGraph();
    }
}
