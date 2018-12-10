package hudson.plugins.disk_usage;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.ProminentProjectAction;
import hudson.model.TopLevelItem;
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
    
    
    public Long getAllSlaveWorkspaces(){
        return getAllSlaveWorkspaces(true);
    }
    
    
    public Long getAllSlaveWorkspaces(boolean cached){
        return getAllDiskUsageWorkspace(cached) - getAllCustomOrNonSlaveWorkspaces(cached);
    }
    
    @Override
    public Long getAllCustomOrNonSlaveWorkspaces(boolean cached){
        ProjectDiskUsage diskUsage = getDiskUsage();
        if(cached){
            return diskUsage.getCachedDiskUsageNonSlaveWorkspace();
        }
        Long size = 0l;
        for(String nodeName: diskUsage.getSlaveWorkspacesUsage().keySet()){
            Node node = null;  
            if(nodeName.isEmpty()){
                node = Jenkins.getInstance();
            }
            else{
                node = Jenkins.getInstance().getNode(nodeName);
            }            
            if(node==null) //slave does not exist
                continue;
            Map<String,Long> paths = diskUsage.getSlaveWorkspacesUsage().get(nodeName);
            for(String path: paths.keySet()){
                Item item = null;
                if(project instanceof TopLevelItem){
                    item = (TopLevelItem) project;
                }
                else{
                    if(project.getParent() instanceof TopLevelItem) {
                        item = (TopLevelItem)project.getParent();
                    }
                }
                try{
                    if(!DiskUsageUtil.isContainedInWorkspace(item, node, path)){ 
                        size += paths.get(path);
                    }
                }
                catch(Exception e){
                    Logger.getLogger(getClass().getName()).log(Level.WARNING, "Can not get workspace for " + item.getDisplayName() + " on " + node.getDisplayName(), e);
                }
            }
        }
        if(project instanceof ItemGroup){
            ItemGroup group = (ItemGroup) project;
            for(Object i:group.getItems()){
                if(i instanceof AbstractProject){
                    AbstractProject p = (AbstractProject) i;
                    ProjectDiskUsageAction action = p.getAction(ProjectDiskUsageAction.class);
                    if(action!=null){
                        size += action.getAllCustomOrNonSlaveWorkspaces(cached);
                    } 
                }
            }
        }
        diskUsage.setCachedDiskUsageNonSlaveWorkspace(size);
        return size;
    }
    
    /**
     * Returns all workspace disku usage including workspace usage its sub-projects
     * 
     * @return disk usage project and its sub-projects
     */
    @Override
    public Long getAllDiskUsageWorkspace(boolean cached){
        ProjectDiskUsage diskUsage = getDiskUsage();
        if(cached){
            return diskUsage.getCachedDiskUsageWorkspace();
        }
         Long size = 0l;
        for(String nodeName: diskUsage.getSlaveWorkspacesUsage().keySet()){
            Node slave = Jenkins.getInstance().getNode(nodeName);
            if(slave==null && !nodeName.isEmpty() && !(slave instanceof Jenkins)) {//slave does not exist
                continue;
            }
            Map<String,Long> paths = diskUsage.getSlaveWorkspacesUsage().get(nodeName);
            for(String path: paths.keySet()){
                    size += paths.get(path);
            }
        }
        if(project instanceof ItemGroup){
            ItemGroup group = (ItemGroup) project;
            for(Object i:group.getItems()){
                if(i instanceof AbstractProject){
                    AbstractProject p = (AbstractProject) i;
                    ProjectDiskUsageAction action = (ProjectDiskUsageAction) p.getAction(ProjectDiskUsageAction.class);
                    if(action!=null){
                        size += action.getAllDiskUsageWorkspace(cached);
                    } 
                }
            }
        }
        diskUsage.setCachedDiskUsageWorkspace(size);
        return size;
    }
    
    public String getSizeInString(Long size){
       return DiskUsageUtil.getSizeString(size);
    }
    
    public Long getDiskUsageWithoutBuilds(){
        return getAllDiskUsageWithoutBuilds(true);
    }
    
    public Long getAllDiskUsageWithoutBuilds(){
        return getAllDiskUsageWithoutBuilds(true);
    }
    
    @Override
    public Long getAllDiskUsageWithoutBuilds(boolean cached){
        ProjectDiskUsage diskUsage = getDiskUsage();
        if(cached){
            return diskUsage.getCachedDiskUsageWithoutBuilds();
        }
        Long size = 0l;
        if(project instanceof ItemGroup){
            size += DiskUsageUtil.getItemGroupAction((ItemGroup)project).getAllDiskUsageWithoutBuilds(cached);               
        }
        else{
            size += diskUsage.getDiskUsageWithoutBuilds();
        }
        diskUsage.setCachedDiskUsageWithoutBuilds(size);
        return size;
    }
    
    
    public Long getJobRootDirDiskUsage(boolean cached) {
        return getBuildsDiskUsage(cached).get("all") + getDiskUsageWithoutBuilds();
    }
    
    public DiskUsageProperty getDiskUsageProperty(){
        DiskUsageProperty property = DiskUsageUtil.getDiskUsageProperty(project);
        return property;
    }
    
    public ProjectDiskUsage getDiskUsage(){
        return DiskUsageUtil.getDiskUsageProperty(project).getDiskUsage();
    }
    
    public Long getAllDiskUsageNotLoadedBuilds(boolean cached) {
       return getBuildsDiskUsage(cached).get("notLoaded");     
    }
    
    
    //todo better to do check somewhere else,it is used for view level too
    private Map<String,Long> getBuildsDiskUsageAllSubItems(ItemGroup group, Date older, Date yonger) {
        ProjectDiskUsage diskUsage = getDiskUsage();
        Map<String,Long> usage = new TreeMap<String,Long>();
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
                    ProjectDiskUsage pUsage = p.getAction(ProjectDiskUsageAction.class).getDiskUsage();
                    Set<DiskUsageBuildInformation> informations = pUsage.getBuildDiskUsage(false);
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
                            if(b.getId().equals(information.getId()) || b.getNumber()==information.getNumber()){
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
                    for(File file : diskUsage.getFilesOfNotLoadedBuilds()){
                      GregorianCalendar calendar = new GregorianCalendar();
                      calendar.setTimeInMillis(file.lastModified());
                      Date date = calendar.getTime();
                      if(older!=null && !date.before(older))
                        continue;
                      if(yonger!=null && !date.after(yonger))
                        continue;
                      Long size = diskUsage.getSizeOfNotLoadedBuild(file.getName());
                      buildsDiskUsage += size;
                      notLoaded += size;
                  }
                }
            }
            
        }
        usage.put("all", buildsDiskUsage);
        usage.put("locked", locked);
        usage.put("notLoaded", notLoaded);
        return usage;
    }
    
    public Map<String, Long> getBuildsDiskUsage() throws IOException {
        return getBuildsDiskUsage(null, null, true);
    }
    
    public Map<String, Long> getBuildsDiskUsage(boolean cached) {
        return getBuildsDiskUsage(null, null, cached);
    }
    
    public Long getAllBuildsDiskUsage(boolean cached) {
        return getBuildsDiskUsage(null, null,cached).get("all");
    }
    
    public Long getAllBuildsDiskUsage() {
        return getBuildsDiskUsage(null, null,true).get("all");
    }
 
    public Map<String, Long> getBuildsDiskUsage(Date older, Date yonger) {
        return getBuildsDiskUsage(older, yonger, true);
    }
    
    public Long getDiskUsageWorkspace(){
        return getAllDiskUsageWorkspace();
    }
    
    public Long getAllDiskUsageWorkspace(){
        return getAllDiskUsageWorkspace(true);
    }
    
    /**
     * @return Disk usage for all builds
     */
    @Override
    public Map<String, Long> getBuildsDiskUsage(Date older, Date younger, boolean cached) {
        ProjectDiskUsage diskUsage = getDiskUsage();
        if(cached && older==null && younger == null){
            // it is necessary go grab all information if it is filtered
            return diskUsage.getCachedBuildDiskUsage();
        }
        Map<String,Long> usage = new TreeMap<String,Long>();
        Long buildsDiskUsage = 0l;
        Long locked = 0l;
        Long notLoaded = 0l;
        if (project != null) {
            if(project instanceof ItemGroup){
               ItemGroup group = (ItemGroup) project;
              Map<String,Long> sizes = getBuildsDiskUsageAllSubItems(group, older, younger);
               buildsDiskUsage += sizes.get("all");
               locked += sizes.get("locked");
               notLoaded += sizes.get("notLoaded");
            }
          Set<DiskUsageBuildInformation> informations = diskUsage.getBuildDiskUsage(false);
          for(DiskUsageBuildInformation information: informations){
            Date date = new Date(information.getTimestamp());
            if(older!=null && !date.before(older))
                continue;
            if(younger!=null && !date.after(younger))
                continue;
            Long size = information.getSize();
            buildsDiskUsage += size;
            Collection<AbstractBuild> loadedBuilds = (Collection<AbstractBuild>) project._getRuns().getLoadedBuilds().values();
            AbstractBuild build = null;
            for (AbstractBuild b : loadedBuilds){
                if(b.getId().equals(information.getId()) || b.getNumber()==information.getNumber()){
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
          for(File file : diskUsage.getFilesOfNotLoadedBuilds()){
              GregorianCalendar calendar = new GregorianCalendar();
              calendar.setTimeInMillis(file.lastModified());
              Date date = calendar.getTime();
              if(older!=null && !date.before(older))
                continue;
              if(younger!=null && !date.after(younger))
                continue;
              Long size = diskUsage.getSizeOfNotLoadedBuild(file.getName());
              buildsDiskUsage += size;
              notLoaded += size;
          }
        }
        usage.put("all", buildsDiskUsage);
        usage.put("locked", locked);
        usage.put("notLoaded", notLoaded);
        diskUsage.setCachedBuildDiskUsage(usage);
        return usage;
    }
    
    public BuildDiskUsageAction getLastBuildAction() {
        Run run = project.getLastBuild();
        if (run != null) {
            return run.getAction(BuildDiskUsageAction.class);
        }

        return null;
    }
    
    public Set<DiskUsageBuildInformation> getBuildsInformation() throws IOException{
        return getDiskUsage().getBuildDiskUsage(false);
    }

    /**
     * Generates a graph with disk usage trend
     *
     * @return XXX
     * @throws IOException XXX
     */
    public Graph getGraph() throws IOException {
        //TODO if(nothing_changed) return;
        List<Object[]> usages = new ArrayList<Object[]>();
        long maxValue = 0;
        long maxValueWorkspace = 0;
        maxValueWorkspace = Math.max(getAllCustomOrNonSlaveWorkspaces(true), getAllSlaveWorkspaces(true));
        Long jobRootDirDiskUsage = getJobRootDirDiskUsage(true);
        maxValue = jobRootDirDiskUsage;
        ProjectDiskUsage diskUsage = getDiskUsage();
        //First iteration just to get scale of the y-axis
        ArrayList<DiskUsageBuildInformation> builds = new ArrayList<DiskUsageBuildInformation>();
        builds.addAll(diskUsage.getBuildDiskUsage(false));
        //do it in reverse order
        for (int i=builds.size()-1; i>=0; i--) {
            DiskUsageBuildInformation build = builds.get(i);
            Long usage = diskUsage.getDiskUsageBuildInformation(build.getId()).getSize();
            if(usage==null || !(usage>0)){
                usage = diskUsage.getDiskUsageBuildInformation(build.getNumber()).getSize();
            }
                usages.add(new Object[]{build.getNumber(), getJobRootDirDiskUsage(true), usage, getAllSlaveWorkspaces(true), getAllCustomOrNonSlaveWorkspaces(true)});
                maxValue = Math.max(maxValue, usage);
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

    /**
     *
     * @param cached XXX
     * @return XXX
     */
    @Override
    public Long getAllDiskUsage(boolean cached) {
        Long size = getAllBuildsDiskUsage(cached) + getAllDiskUsageWithoutBuilds(cached) + getAllDiskUsageNotLoadedBuilds(cached);
        if(project instanceof ItemGroup){
            DiskUsageItemGroup group = DiskUsageUtil.getItemGroupAction((ItemGroup)project).getDiskUsageItemGroup();
            size += group.getDiskUsageOfNotLoadedJobs(cached);
        }
        return size;
    }

    /**
     * Shortcut for the jelly view
     *
     * @return XXX
     */
    public boolean showGraph() {
        return Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().isShowGraph();
    }


    private void actualizeCachedData(boolean parent) {
        ProjectDiskUsage diskUsage = getDiskUsage();
        diskUsage.setCachedBuildDiskUsage(getBuildsDiskUsage(false));
        diskUsage.setCachedDiskUsageNonSlaveWorkspace(getAllCustomOrNonSlaveWorkspaces(false));
        diskUsage.setCachedDiskUsageWithoutBuilds(getAllDiskUsageWithoutBuilds(false));
        diskUsage.setCachedDiskUsageWorkspace(getAllDiskUsageWorkspace(false));
        ItemGroup group = project.getParent();
        if(group!=null && parent){
            DiskUsageUtil.getItemGroupAction(group).actualizeCachedData();
        }
    }

    @Override
    public void actualizeCachedData() {
        actualizeCachedData(true);
    }

   @Override
    public void actualizeCachedBuildsData() {
       getDiskUsage().setCachedBuildDiskUsage(getBuildsDiskUsage(null, null, false));
        if(project.getParent() != null){
            DiskUsageUtil.getItemGroupAction(project.getParent()).actualizeCachedBuildsData();
        }
    } 

    @Override
    public void actualizeCachedWorkspaceData() {
        getDiskUsage().setCachedDiskUsageWorkspace(getAllDiskUsageWorkspace(false));
        if(project.getParent() != null){
            DiskUsageUtil.getItemGroupAction(project.getParent()).actualizeCachedWorkspaceData();
        }
    }

    @Override
    public void actualizeCachedNotCustomWorkspaceData() {
        getDiskUsage().setCachedDiskUsageNonSlaveWorkspace(getAllCustomOrNonSlaveWorkspaces(false));
        if(project.getParent() != null){
            DiskUsageUtil.getItemGroupAction(project.getParent()).actualizeCachedNotCustomWorkspaceData();
        }
    }

    @Override
    public void actualizeCachedJobWithoutBuildsData() {
        getDiskUsage().setCachedDiskUsageWithoutBuilds(getAllDiskUsageWithoutBuilds(false));
        if(project.getParent() != null){
            DiskUsageUtil.getItemGroupAction(project.getParent()).actualizeCachedJobWithoutBuildsData();
        }
    }
    
     @Override
    public void actualizeAllCachedDate() {
        actualizeCachedJobWithoutBuildsData();
        actualizeCachedNotCustomWorkspaceData();
        actualizeCachedWorkspaceData();
        actualizeCachedBuildsData();
    }
}
