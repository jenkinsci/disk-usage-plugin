package hudson.plugins.disk_usage;

import hudson.plugins.disk_usage.unused.DiskUsageNotUsedDataCalculationThread;
import hudson.Extension;
import hudson.Plugin;
import hudson.Util;
import hudson.model.*;
import hudson.model.Item;
import hudson.model.RootAction;
import hudson.plugins.disk_usage.unused.DiskUsageItemGroup;
import hudson.security.Permission;
import hudson.util.Graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.jfree.data.category.DefaultCategoryDataset;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Entry point of the the plugin.
 *
 * @author dvrzalik
 */
@Extension
public class DiskUsagePlugin extends Plugin {
    
//    private Long diskUsageBuilds = 0l;
//    private Long diskUsageJobsWithoutBuilds = 0l;
//    private Long diskUsageNotLoadedJobs = 0l;
//    private Long diskUsageNotLoadedBuilds = 0l;
//    private Long diskUsageWorkspaces = 0l;
//    private Long diskUsageLockedBuilds = 0l;
//    private Long diskUsageNonSlaveWorkspaces = 0l;
    
    private Map<ItemGroup,DiskUsageItemGroup> diskUsageItemGroups = new ConcurrentHashMap<ItemGroup,DiskUsageItemGroup>(); 
    
    public DiskUsagePlugin(){

    }

    /**
     *
     * @param group XXX
     * @param diskUsage XXX
     */
    public void addNewItemGroup(ItemGroup group, DiskUsageItemGroup diskUsage){
        DiskUsageItemGroup usage = diskUsageItemGroups.get(group);
        diskUsageItemGroups.put(group, diskUsage);
        if(usage!=null){
            usage.load();
        }
    }

    protected void loadDiskUsageItemGroups(){
        diskUsageItemGroups.clear();
        List<Action> actions = Jenkins.getInstance().getActions();
        for(Action a : actions){
            if(a instanceof DiskUsageJenkinsAction){
                DiskUsageJenkinsAction jenkinsDUAction = (DiskUsageJenkinsAction) a;
                diskUsageItemGroups.put(Jenkins.getInstance(), jenkinsDUAction.getDiskUsageItemGroup());
                loadAllDiskUsageForSubItemGroups(Jenkins.getInstance());
                return;
            }
        }
        loadAllDiskUsageItemGroups(Jenkins.getInstance());
    }

    /**
     *
     * @param group XXX
     */
    public void loadAllDiskUsageForSubItemGroups(ItemGroup group){
        for(Item item : (Collection<Item>)group.getItems()){
            if(item instanceof ItemGroup){
                loadAllDiskUsageItemGroups((ItemGroup)item);
            }
        }
    }

    protected void loadAllDiskUsageItemGroups(ItemGroup group){
        DiskUsageItemGroup diskUsage = new DiskUsageItemGroup(group);
        diskUsage.load();
        diskUsageItemGroups.put(group,diskUsage);
        for(Item item : (Collection<Item>)group.getItems()){
            if(item instanceof ItemGroup){
                loadAllDiskUsageItemGroups((ItemGroup)item);
            }
        }
    }

    /**
     *
     * @return XXX
     */
    public Map<ItemGroup,DiskUsageItemGroup> getDiskUsageItemGroups(){
        return diskUsageItemGroups;
    }
    
    protected DiskUsageItemGroup loadDiskUsageItemGroupForItemGroup(ItemGroup group){
        DiskUsageItemGroup diskUsage = new DiskUsageItemGroup(group);
        if(diskUsage.getConfigFile().exists()){
            diskUsage.load();
        }
        //new one
        else{
            diskUsage.save();
        }
        diskUsageItemGroups.put(group,diskUsage);
        return diskUsage;
    }

    /**
     *
     * @return XXX
     */
    public DiskUsageItemGroup getDiskUsageItemGrouForJenkinsRootAction(){
        DiskUsageItemGroup usage = diskUsageItemGroups.get(Jenkins.getInstance());
        if(usage==null){
            usage = new DiskUsageItemGroup(Jenkins.getInstance());
            usage.load();
            diskUsageItemGroups.put(Jenkins.getInstance(),usage);
        }
        return usage;
    }

    /**
     *
     * @param group XXX
     * @return XXX
     */
    public DiskUsageItemGroup getDiskUsageItemGroup(ItemGroup group){
        DiskUsageItemGroup usage = diskUsageItemGroups.get(group);
        if(usage==null){
            return loadDiskUsageItemGroupForItemGroup(group);
        }
        return usage;
    }

    /**
     *
     * @param group XXX
     * @throws IOException XXX
     */
    public void putDiskUsageItemGroup(ItemGroup group) throws IOException{
        if(!diskUsageItemGroups.containsKey(group)){
            DiskUsageItemGroup usage = new DiskUsageItemGroup(group);
            diskUsageItemGroups.put(group, usage);
            usage.save();
        }
    }

    /**
     *
     * @param group XXX
     */
    public void removeDiskUsageItemGroup(ItemGroup group){
        diskUsageItemGroups.remove(group);
    }
    
//    public void loadNotUsedDataDiskUsage(){
//        diskUsageGroupItems.load();
//    }

    /**
     *
     * @throws IOException XXX
     */
    public void refreshGlobalInformation() throws IOException{
        DiskUsageJenkinsAction.getInstance().actualizeAllCachedDate();
    }

    /**
     *
     * @return XXX
     */
    public Long getCachedGlobalBuildsDiskUsage(){
        return getDiskUsageItemGrouForJenkinsRootAction().getCaschedDiskUsageBuilds().get("all");
    }

    /**
     *
     * @return XXX
     */
    public Long getCachedGlobalJobsDiskUsage(){
        return (getCachedGlobalBuildsDiskUsage() + getCachedGlobalJobsWithoutBuildsDiskUsage());
    }

    /**
     *
     * @return XXX
     */
    public Long getCachedGlobalJobsWithoutBuildsDiskUsage(){
        return getDiskUsageItemGrouForJenkinsRootAction().getCachedDiskUsageWithoutBuilds();
    }

    /**
     *
     * @return XXX
     */
    public Long getCachedGlobalLockedBuildsDiskUsage(){
     return getDiskUsageItemGrouForJenkinsRootAction().getCaschedDiskUsageBuilds().get("locked");
    }

    /**
     *
     * @return XXX
     */
    public Long getCachedGlobalNotLoadedBuildsDiskUsage(){
     return getDiskUsageItemGrouForJenkinsRootAction().getCaschedDiskUsageBuilds().get("notLoaded");
    }

    /**
     *
     * @return XXX
     */
    public Long getCachedGlobalWorkspacesDiskUsage(){
        return getDiskUsageItemGrouForJenkinsRootAction().getCachedDiskUsageWorkspaces();
    }

    /**
     *
     * @return XXX
     */
    public Long getCachedNonSlaveDiskUsageWorkspace(){
        return getDiskUsageItemGrouForJenkinsRootAction().getCachedDiskUsageCustomWorkspaces();
    }

    /**
     *
     * @return XXX
     */
    public Long getCachedSlaveDiskUsageWorkspace(){
        return getCachedGlobalWorkspacesDiskUsage() - getCachedNonSlaveDiskUsageWorkspace();
    }

    /**
     *
     * @return XXX
     * @throws IOException XXX
     */
    public Long getGlobalBuildsDiskUsage() throws IOException{
        refreshGlobalInformation();
        return getCachedGlobalBuildsDiskUsage();
    }

    /**
     *
     * @return XXX
     * @throws IOException XXX
     */
    public Long getGlobalJobsDiskUsage() throws IOException{
        refreshGlobalInformation();
        return getCachedGlobalJobsDiskUsage();
    }

    /**
     *
     * @return XXX
     * @throws IOException XXX
     */
    public Long getGlobalJobsWithoutBuildsDiskUsage() throws IOException{
        refreshGlobalInformation();
        return getCachedGlobalJobsWithoutBuildsDiskUsage();
    }

    /**
     *
     * @return XXX
     * @throws IOException XXX
     */
    public Long getGlobalWorkspacesDiskUsage() throws IOException{
        refreshGlobalInformation();
        return this.getCachedGlobalWorkspacesDiskUsage();
    }

    /**
     *
     * @return XXX
     * @throws IOException XXX
     */
    public Long getGlobalNonSlaveDiskUsageWorkspace() throws IOException{
        refreshGlobalInformation();
        return getCachedNonSlaveDiskUsageWorkspace();
    }

    /**
     *
     * @return XXX
     * @throws IOException XXX
     */
    public Long getGlobalSlaveDiskUsageWorkspace() throws IOException{
        refreshGlobalInformation();
        return getCachedSlaveDiskUsageWorkspace();
    }

    /**
     *
     * @return XXX
     * @throws IOException XXX
     */
    public Long getGlobalNotLoadedBuildsDiskUsageWorkspace() throws IOException{
        refreshGlobalInformation();
        return getCachedGlobalNotLoadedBuildsDiskUsage();
    }

    /**
     *
     * @return XXX
     */
    public BuildDiskUsageCalculationThread getBuildsDiskUsageThread(){
        return AperiodicWork.all().get(BuildDiskUsageCalculationThread.class);
    }

    /**
     *
     * @return XXX
     */
    public JobWithoutBuildsDiskUsageCalculation getJobsDiskUsageThread(){
        return AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class);
    }

    /**
     *
     * @return XXX
     */
    public WorkspaceDiskUsageCalculationThread getWorkspaceDiskUsageThread(){
       return AperiodicWork.all().get(WorkspaceDiskUsageCalculationThread.class); 
    }

    /**
     *
     * @return XXX
     */
    public DiskUsageNotUsedDataCalculationThread getNotUsedDataDiskUsageThread(){
       return AperiodicWork.all().get(DiskUsageNotUsedDataCalculationThread.class); 
    }
    
    /**
     * @param project foo
     * @return DiskUsage for given project (shortcut for the view). Never null.
     */
    public ProjectDiskUsageAction getDiskUsage(Job project) {
        ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
        return action;
    }

    /**
     *
     * @param size XXX
     * @return XXX
     */
    public String getDiskUsageInString(Long size){
        return DiskUsageUtil.getSizeString(size);
    }

    /**
     * @return Project list sorted by occupied disk space
     * @throws IOException blah
     */
    public List getProjectList() throws IOException {
        refreshGlobalInformation();
        Comparator<AbstractProject> comparator = new Comparator<AbstractProject>() {

            public int compare(AbstractProject o1, AbstractProject o2) {
                
                ProjectDiskUsageAction dua1 = getDiskUsage(o1);
                ProjectDiskUsageAction dua2 = getDiskUsage(o2);
                long result = dua2.getJobRootDirDiskUsage(true) + dua2.getAllDiskUsageWorkspace(true) - dua1.getJobRootDirDiskUsage(true) - dua1.getAllDiskUsageWorkspace(true);
                
                if(result > 0) return 1;
                if(result < 0) return -1;
                return 0;
            }
        };

        List<AbstractProject> projectList = Jenkins.getInstance().getAllItems(AbstractProject.class);
        Collections.sort(projectList, comparator);

        return projectList;
    }

    /**
     *
     * @param req XXX
     * @param rsp XXX
     * @throws ServletException XXX
     * @throws IOException XXX
     */
    public void doFilter(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException{
        Date older = DiskUsageUtil.getDate(req.getParameter("older"), req.getParameter("olderUnit"));
        Date younger = DiskUsageUtil.getDate(req.getParameter("younger"), req.getParameter("youngerUnit"));
        req.setAttribute("filter", "filter");
        req.setAttribute("older", older);
        req.setAttribute("younger", younger);
        
        req.getView(this, "index.jelly").forward(req, rsp);     
    }
    
    public DiskUsageProjectActionFactory.DescriptorImpl getConfiguration(){
        return DiskUsageProjectActionFactory.DESCRIPTOR;
    }
    
    public Graph getOverallGraph(){
        File jobsDir = new File(Jenkins.getInstance().getRootDir(), "jobs");
        long maxValue = getCachedGlobalJobsDiskUsage();
        if(getConfiguration().getShowFreeSpaceForJobDirectory()){
            maxValue = jobsDir.getTotalSpace();
        }
        long maxValueWorkspace = Math.max(getCachedNonSlaveDiskUsageWorkspace(), getCachedSlaveDiskUsageWorkspace());
        List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> record = DiskUsageProjectActionFactory.DESCRIPTOR.getHistory();
        //First iteration just to get scale of the y-axis
        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : record){
            if(getConfiguration().getShowFreeSpaceForJobDirectory()){
                maxValue = Math.max(maxValue,usage.getAllSpace());
            }
            maxValue = Math.max(maxValue, usage.getJobsDiskUsage());
            maxValueWorkspace = Math.max(maxValueWorkspace, usage.getSlaveWorkspacesUsage());
            maxValueWorkspace = Math.max(maxValueWorkspace, usage.getNonSlaveWorkspacesUsage());           
        }
        int floor = (int) DiskUsageUtil.getScale(maxValue);
        int floorWorkspace = (int) DiskUsageUtil.getScale(maxValueWorkspace);
        String unit = DiskUsageUtil.getUnitString(floor);
        String unitWorkspace = DiskUsageUtil.getUnitString(floorWorkspace);
        double base = Math.pow(1024, floor);
        double baseWorkspace = Math.pow(1024, floorWorkspace);
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        DefaultCategoryDataset datasetW = new DefaultCategoryDataset();
        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : record) {
            Date label = usage.getDate();
            if(getConfiguration().getShowFreeSpaceForJobDirectory()){
                dataset.addValue(((Long) usage.getAllSpace()) / base, "space for jobs directory", label);
            }
            dataset.addValue(((Long) usage.getJobsDiskUsage()) / base, "all jobs", label);
            dataset.addValue(((Long) usage.getBuildsDiskUsage()) / base, "all builds", label);
            datasetW.addValue(((Long) usage.getSlaveWorkspacesUsage()) / baseWorkspace, "slave workspaces", label); 
            datasetW.addValue(((Long) usage.getNonSlaveWorkspacesUsage()) / baseWorkspace, "non slave workspaces", label); 
        }
        
        //add current state
        if(getConfiguration().getShowFreeSpaceForJobDirectory()){
                dataset.addValue(((Long) jobsDir.getTotalSpace()) / base, "space for jobs directory", "current");
        }
        dataset.addValue(((Long) getCachedGlobalJobsDiskUsage()) / base, "all jobs", "current");
        dataset.addValue(((Long) getCachedGlobalBuildsDiskUsage()) / base, "all builds", "current");
        datasetW.addValue(((Long) getCachedSlaveDiskUsageWorkspace()) / baseWorkspace, "slave workspaces", "current");
        datasetW.addValue(((Long) getCachedNonSlaveDiskUsageWorkspace()) / baseWorkspace, "non slave workspaces", "current");
        return  new DiskUsageGraph(dataset, unit, datasetW, unitWorkspace);
    }

    /**
     *
     * @param req XXX
     * @param res XXX
     * @throws ServletException XXX
     * @throws IOException XXX
     * @throws Exception XXX
     */
    public void doRecordBuildDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationBuildsEnabled() && !getBuildsDiskUsageThread().isExecuting())
            getBuildsDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }

    /**
     *
     * @param req XXX
     * @param res XXX
     * @throws ServletException XXX
     * @throws IOException XXX
     * @throws Exception XXX
     */
    public void doRecordJobsDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationJobsEnabled() && !getJobsDiskUsageThread().isExecuting())
            getJobsDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }

    /**
     *
     * @param req XXX
     * @param res XXX
     * @throws ServletException XXX
     * @throws IOException XXX
     * @throws Exception XXX
     */
    public void doRecordWorkspaceDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationWorkspaceEnabled() && !getWorkspaceDiskUsageThread().isExecuting())
            getWorkspaceDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }

    /**
     *
     * @param req XXX
     * @param res XXX
     * @throws ServletException XXX
     * @throws IOException XXX
     * @throws Exception XXX
     */
    public void doRecordNotUsedDataDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationNotUsedDataEnabled() && !getNotUsedDataDiskUsageThread().isExecuting())
            getNotUsedDataDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }

    /**
     *
     * @return XXX
     */
    public String getCountIntervalForBuilds(){
        long nextExecution = getBuildsDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
        if(nextExecution<=0) //not scheduled
            nextExecution = getBuildsDiskUsageThread().getRecurrencePeriod();    
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }

    /**
     *
     * @return XXX
     */
    public String getCountIntervalForJobs(){
        long nextExecution = getJobsDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
        if(nextExecution<=0) //not scheduled
            nextExecution = getJobsDiskUsageThread().getRecurrencePeriod();
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }

    /**
     *
     * @return XXX
     */
    public String getCountIntervalForWorkspaces(){
        long nextExecution = getWorkspaceDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
            if(nextExecution<=0) //not scheduled
            nextExecution = getWorkspaceDiskUsageThread().getRecurrencePeriod();
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }

    /**
     *
     * @return XXX
     */
    public boolean hasAdministrativePermission(){
        return Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER);
    }

    /**
     *
     * @return XXX
     */
    public String getCountIntervalForNotUsedData(){
        long nextExecution = getNotUsedDataDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
            if(nextExecution<=0) //not scheduled
            nextExecution = getNotUsedDataDiskUsageThread().getRecurrencePeriod();
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }

    /**
     *
     * @return XXX
     */
    public String getTotalSizeOfNotLoadedBuilds(){
        Long size = 0L;
        for(Item item : Jenkins.getInstance().getItems()){
            if(item instanceof AbstractProject){
                AbstractProject project = (AbstractProject) item;
                ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
                size += action.getAllDiskUsageNotLoadedBuilds(true);
            }
        }
        return DiskUsageUtil.getSizeString(size);
    }

    /**
     *
     * @return XXX
     */
    public Map<AbstractProject,Long> getNotLoadedBuilds(){
        Map<AbstractProject,Long> notLoadedBuilds = new HashMap<AbstractProject,Long>();
        for(Item item : Jenkins.getInstance().getItems()){
            if(item instanceof AbstractProject){
                AbstractProject project = (AbstractProject) item;
                ProjectDiskUsage usage = DiskUsageUtil.getDiskUsageProperty(project).getDiskUsage();
                ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
                if(!usage.getNotLoadedBuilds().isEmpty()){
                    notLoadedBuilds.put(project, action.getAllDiskUsageNotLoadedBuilds(true));
                }
            }
        }
        return notLoadedBuilds;
    }

    /**
     *
     * @param req XXX
     * @param res XXX
     * @throws IOException XXX
     * @throws ServletException XXX
     */
    public void doUnused(StaplerRequest req, StaplerResponse res) throws IOException, ServletException{
        @SuppressWarnings("unused")
        Map<AbstractProject,Map<String,Long>> notLoadedBuilds = new HashMap<AbstractProject,Map<String,Long>>();
        @SuppressWarnings("unused")
        Map<String,Long> notLoadedJobs = new HashMap<String,Long>();
        req.getView(this, "unused.jelly").forward(req, res);
    }
    
}
