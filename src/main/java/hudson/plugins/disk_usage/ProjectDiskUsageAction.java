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


    public Long getAllAgentWorkspaces(){
        return getAllAgentWorkspaces(true);
    }


    public Long getAllAgentWorkspaces(boolean cashed){
        return getAllDiskUsageWorkspace(cashed) - getAllCustomOrNonAgentWorkspaces(cashed);
    }

    @Override
    public Long getAllCustomOrNonAgentWorkspaces(boolean cashed){
        ProjectDiskUsage diskUsage = getDiskUsage();
        if(cashed){
            return diskUsage.getCashedDiskUsageNonAgentWorkspace();
        }
        Long size = 0l;
        for(String nodeName: diskUsage.getAgentWorkspacesUsage().keySet()){
            Node node = null;
            if(nodeName.isEmpty()){
                node = Jenkins.getInstance();
            }
            else{
                node = Jenkins.getInstance().getNode(nodeName);
            }
            if(node==null) //agent does not exist
                continue;
            Map<String,Long> paths = diskUsage.getAgentWorkspacesUsage().get(nodeName);
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
                        size += action.getAllCustomOrNonAgentWorkspaces(cashed);
                    }
                }
            }
        }
        diskUsage.setCashedDiskUsageNonAgentWorkspace(size);
        return size;
    }

    /**
     * Returns all workspace disku usage including workspace usage its sub-projects
     *
     * @return disk usage project and its sub-projects
     */
    @Override
    public Long getAllDiskUsageWorkspace(boolean cashed){
        ProjectDiskUsage diskUsage = getDiskUsage();
        if(cashed){
            return diskUsage.getCashedDiskUsageWorkspace();
        }
         Long size = 0l;
        for(String nodeName: diskUsage.getAgentWorkspacesUsage().keySet()){
            Node agent = Jenkins.getInstance().getNode(nodeName);
            if(agent==null && !nodeName.isEmpty() && !(agent instanceof Jenkins)) {//agent does not exist
                continue;
            }
            Map<String,Long> paths = diskUsage.getAgentWorkspacesUsage().get(nodeName);
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
                        size += action.getAllDiskUsageWorkspace(cashed);
                    }
                }
            }
        }
        diskUsage.setCashedDiskUsageWorkspace(size);
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
    public Long getAllDiskUsageWithoutBuilds(boolean cashed){
        ProjectDiskUsage diskUsage = getDiskUsage();
        if(cashed){
            return diskUsage.getCashedDiskUsageWithoutBuilds();
        }
        Long size = 0l;
        if(project instanceof ItemGroup){
            size += DiskUsageUtil.getItemGroupAction((ItemGroup)project).getAllDiskUsageWithoutBuilds(cashed);
        }
        else{
            size += diskUsage.getDiskUsageWithoutBuilds();
        }
        diskUsage.setCashedDiskUsageWithoutBuilds(size);
        return size;
    }


    public Long getJobRootDirDiskUsage(boolean cashed) {
        return getBuildsDiskUsage(cashed).get("all") + getDiskUsageWithoutBuilds();
    }

    public DiskUsageProperty getDiskUsageProperty(){
        DiskUsageProperty property = DiskUsageUtil.getDiskUsageProperty(project);
        return property;
    }

    public ProjectDiskUsage getDiskUsage(){
        return DiskUsageUtil.getDiskUsageProperty(project).getDiskUsage();
    }

    public Long getAllDiskUsageNotLoadedBuilds(boolean cashed) {
       return getBuildsDiskUsage(cashed).get("notLoaded");
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

    public Map<String, Long> getBuildsDiskUsage(boolean cashed) {
        return getBuildsDiskUsage(null, null, cashed);
    }

    public Long getAllBuildsDiskUsage(boolean cashed) {
        return getBuildsDiskUsage(null, null,cashed).get("all");
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
    public Map<String, Long> getBuildsDiskUsage(Date older, Date younger, boolean cashed) {
        ProjectDiskUsage diskUsage = getDiskUsage();
        if(cashed && older==null && younger == null){
            // it is necessary go grab all information if it is filtered
            return diskUsage.getCashedBuildDiskUsage();
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
        diskUsage.setCashedBuildDiskUsage(usage);
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
     */
    public Graph getGraph() throws IOException {
        //TODO if(nothing_changed) return;
        List<Object[]> usages = new ArrayList<Object[]>();
        long maxValue = 0;
        long maxValueWorkspace = 0;
        maxValueWorkspace = Math.max(getAllCustomOrNonAgentWorkspaces(true), getAllAgentWorkspaces(true));
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
                usages.add(new Object[]{build.getNumber(), getJobRootDirDiskUsage(true), usage, getAllAgentWorkspaces(true), getAllCustomOrNonAgentWorkspaces(true)});
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
                    Messages.DiskUsage_Graph_AgentWorkspaces(), label);
            dataset2.addValue(((Long) usage[4]) / workspaceBase,
                    Messages.DiskUsage_Graph_NonAgentWorkspaces(), label);
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
    public Long getAllDiskUsage(boolean cashed) {
        Long size = getAllBuildsDiskUsage(cashed) + getAllDiskUsageWithoutBuilds(cashed) + getAllDiskUsageNotLoadedBuilds(cashed);
        if(project instanceof ItemGroup){
            DiskUsageItemGroup group = DiskUsageUtil.getItemGroupAction((ItemGroup)project).getDiskUsageItemGroup();
            size += group.getDiskUsageOfNotLoadedJobs(cashed);
        }
        return size;
    }

    /** Shortcut for the jelly view */
    public boolean showGraph() {
        return Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().isShowGraph();
    }


    private void actualizeCashedData(boolean parent) {
        ProjectDiskUsage diskUsage = getDiskUsage();
        diskUsage.setCashedBuildDiskUsage(getBuildsDiskUsage(false));
        diskUsage.setCashedDiskUsageNonAgentWorkspace(getAllCustomOrNonAgentWorkspaces(false));
        diskUsage.setCashedDiskUsageWithoutBuilds(getAllDiskUsageWithoutBuilds(false));
        diskUsage.setCashedDiskUsageWorkspace(getAllDiskUsageWorkspace(false));
        ItemGroup group = project.getParent();
        if(group!=null && parent){
            DiskUsageUtil.getItemGroupAction(group).actualizeCashedData();
        }
    }

    @Override
    public void actualizeCashedData() {
        actualizeCashedData(true);
    }

   @Override
    public void actualizeCashedBuildsData() {
       getDiskUsage().setCashedBuildDiskUsage(getBuildsDiskUsage(null, null, false));
        if(project.getParent() != null){
            DiskUsageUtil.getItemGroupAction(project.getParent()).actualizeCashedBuildsData();
        }
    }

    @Override
    public void actualizeCashedWorkspaceData() {
        getDiskUsage().setCashedDiskUsageWorkspace(getAllDiskUsageWorkspace(false));
        if(project.getParent() != null){
            DiskUsageUtil.getItemGroupAction(project.getParent()).actualizeCashedWorkspaceData();
        }
    }

    @Override
    public void actualizeCashedNotCustomWorkspaceData() {
        getDiskUsage().setCashedDiskUsageNonAgentWorkspace(getAllCustomOrNonAgentWorkspaces(false));
        if(project.getParent() != null){
            DiskUsageUtil.getItemGroupAction(project.getParent()).actualizeCashedNotCustomWorkspaceData();
        }
    }

    @Override
    public void actualizeCashedJobWithoutBuildsData() {
        getDiskUsage().setCashedDiskUsageWithoutBuilds(getAllDiskUsageWithoutBuilds(false));
        if(project.getParent() != null){
            DiskUsageUtil.getItemGroupAction(project.getParent()).actualizeCashedJobWithoutBuildsData();
        }
    }

     @Override
    public void actualizeAllCashedDate() {
        actualizeCashedJobWithoutBuildsData();
        actualizeCashedNotCustomWorkspaceData();
        actualizeCashedWorkspaceData();
        actualizeCashedBuildsData();
    }
}
