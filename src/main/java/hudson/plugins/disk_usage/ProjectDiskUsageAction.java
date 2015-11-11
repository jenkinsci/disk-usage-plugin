package hudson.plugins.disk_usage;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.ItemGroup;
import hudson.model.ProminentProjectAction;
import hudson.plugins.disk_usage.unused.DiskUsageItemGroup;
import hudson.util.Graph;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.jfree.data.category.DefaultCategoryDataset;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Disk usage of a project
 * 
 * @author dvrzalik
 */
@ExportedBean(defaultVisibility = 1)
public class ProjectDiskUsageAction implements ProminentProjectAction, DiskUsageItemAction {

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
        if(property==null){
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
        }
        return property.getAllWorkspaceSize();
    }
    
    public Long getAllSlaveWorkspaces(){
        return getAllDiskUsageWorkspace() - getAllCustomOrNonSlaveWorkspaces();
    }
    
    @Override
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
    @Override
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
    
    @Override
    public Long getDiskUsageWithoutBuilds(){
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null){
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
        }
        return property.getDiskUsageWithoutBuilds();
    }
    
    public Long getAllDiskUsageWithoutBuilds(){
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null){
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
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
    
    public DiskUsageProperty getDiskUsageProperty(){
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        return property;
    }
    
    public String getDiskUsageNotLoadedBuilds(){
        return DiskUsageUtil.getSizeString(getSizeOfAllNotLoadedBuilds());
        
    }
    
    public ProjectDiskUsage getDiskUsage(){
        return getDiskUsageProperty().getDiskUsage();
    }
    
    public Long getSizeOfNotLoadedBuilds(){
        Long size = 0L;
        for(Long s : getDiskUsage().getUnusedBuilds().values()){
            size += s;
        }
        return size;
    }
    
    public Long getSizeOfAllNotLoadedBuilds(){
       Long size = getSizeOfNotLoadedBuilds();
       if(project instanceof ItemGroup){
           ItemGroup group = (ItemGroup) project;
           for(Item item : (Collection<Item>) group.getItems()){
               if(item instanceof AbstractProject){
                   AbstractProject p = (AbstractProject) item;
                   ProjectDiskUsageAction action = p.getAction(getClass());
                   size += action.getSizeOfAllNotLoadedBuilds();
               }
           }
       }
        return size;
    }
    
    private Map<String,Long> getBuildsDiskUsageAllSubItems(ItemGroup group, Date older, Date yonger) {
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
                        DiskUsageUtil.addProperty(project);
                        property = project.getProperty(DiskUsageProperty.class);
                    }
                    Set<DiskUsageBuildInformation> informations = property.getDiskUsageOfBuilds();
                    for(DiskUsageBuildInformation information: informations){
                        Date date = new Date(information.getTimestamp());
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
                        if(build!=null && build.isKeepLog()){
                            locked += size;
                        }
                        else{
                            if(information.isLocked()){
                                locked += size;
                            }
                        }
                        
                    }
                    for(File file : property.getDiskUsage().getFilesOfNotLoadedBuilds()){
                      GregorianCalendar calendar = new GregorianCalendar();
                      calendar.setTimeInMillis(file.lastModified());
                      Date date = calendar.getTime();
                      if(older!=null && !date.before(older))
                        continue;
                      if(yonger!=null && !date.after(yonger))
                        continue;
                      Long size = property.getDiskUsage().getSizeOfNotLoadedBuild(file.getName());
                      buildsDiskUsage += size;
                      notLoaded += size;
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
    
    public Long getAllBuildsDiskUsage() {
        return getBuildsDiskUsage(null, null).get("all");
    }
    
    /**
     * @return Disk usage for all builds
     */
    @Override
    public Map<String, Long> getBuildsDiskUsage(Date older, Date yonger) {
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null){
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
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
            Date date = new Date(information.getTimestamp());
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
            if(build!=null && build.isKeepLog()){
                locked += size;
            }
            else{
                if(information.isLocked()){
                    locked += size;
                }
                
            }
          }
          for(File file : property.getDiskUsage().getFilesOfNotLoadedBuilds()){
              GregorianCalendar calendar = new GregorianCalendar();
              calendar.setTimeInMillis(file.lastModified());
              Date date = calendar.getTime();
              if(older!=null && !date.before(older))
                continue;
              if(yonger!=null && !date.after(yonger))
                continue;
              Long size = property.getDiskUsage().getSizeOfNotLoadedBuild(file.getName());
              buildsDiskUsage += size;
              notLoaded += size;
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
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
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
        Long jobRootDirDiskUsage = getJobRootDirDiskUsage();
        maxValue = jobRootDirDiskUsage;
        //First iteration just to get scale of the y-axis
        ArrayList<DiskUsageBuildInformation> builds = new ArrayList<DiskUsageBuildInformation>();
        builds.addAll(property.getDiskUsageOfBuilds());
        //do it in reverse order
        for (int i=builds.size()-1; i>=0; i--) {
            DiskUsageBuildInformation build = builds.get(i);
            Long diskUsage = property.getDiskUsageOfBuild(build.getId());
                usages.add(new Object[]{build.getNumber(), getJobRootDirDiskUsage(), diskUsage, getAllSlaveWorkspaces(), getAllCustomOrNonSlaveWorkspaces()});
                maxValue = Math.max(maxValue, diskUsage);
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
            Integer label = ((Integer) usage[0]);
            dataset.addValue(((Long) usage[1]) / base,
                    Messages.DiskUsage_Graph_JobDirectory(), label);
            dataset.addValue(((Long) usage[2]) / base,
                    Messages.DiskUsage_Graph_BuildDirectory(), label);
            dataset2.addValue(((Long) usage[3]) / workspaceBase,
                    Messages.DiskUsage_Graph_SlaveWorkspaces(), label);
            dataset2.addValue(((Long) usage[4]) / workspaceBase,
                    Messages.DiskUsage_Graph_NonSlaveWorkspaces(), label);
        }
        return new DiskUsageGraph(dataset, unit, dataset2, workspaceUnit);
    }
    
    public void doDelete(StaplerRequest req, StaplerResponse res) throws IOException, ServletException{
        String buildId = req.getParameter("buildId");
        File file = new File(project.getBuildDir(), buildId);
        Util.deleteRecursive(file);
        getDiskUsage().removeDeletedNotLoadedBuild(buildId);
        req.getView(this, "index.jelly").forward(req, res);
    }
    
    public void doReload(StaplerRequest req, StaplerResponse res) throws IOException, ServletException{
        String buildId = req.getParameter("buildId");
        AbstractBuild build = project.getBuild(buildId);
        if(build==null){
            req.setAttribute("errorMessage","Build " + buildId+ " can not be loaded. Please, check Jenkins log for details.");
        }
        else{
            getDiskUsage().moveToLoadedBuilds(build, getDiskUsage().getSizeOfNotLoadedBuild(buildId));
        }
        req.getView(this, "index.jelly").forward(req, res);
    }
    
    @Override
    public Long getAllDiskUsage() {
        Long size = getAllBuildsDiskUsage() + getAllDiskUsageWithoutBuilds() + getSizeOfAllNotLoadedBuilds();
        if(project instanceof ItemGroup){
            DiskUsageItemGroup group = DiskUsageUtil.getItemGroupAction((ItemGroup)project).getDiskUsageItemGroup();
            size += group.getDiskUsageOfNotLoadedJobs();
        }
        return size;
    }

    /** Shortcut for the jelly view */
    public boolean showGraph() {
        return Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().isShowGraph();
    }
}
