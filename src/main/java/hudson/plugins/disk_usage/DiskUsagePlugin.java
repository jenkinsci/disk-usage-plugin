package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.Plugin;
import hudson.Util;
import hudson.model.*;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.jfree.data.category.DefaultCategoryDataset;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Entry point of the the plugin.
 *
 * @author dvrzalik
 * @plugin
 */
@Extension
public class DiskUsagePlugin extends Plugin {
    
    private Long diskUsageBuilds = 0l;
    private Long diskUsageJobsWithoutBuilds = 0l;
    private Long diskUsageWorkspaces = 0l;
    private Long diskUsageLockedBuilds = 0l;
    
    public DiskUsagePlugin(){
    }
    
    public void refreshGlobalInformation(){
        diskUsageBuilds = 0l;
            diskUsageWorkspaces = 0l;
            diskUsageJobsWithoutBuilds = 0l;
            diskUsageLockedBuilds = 0l;
        for(Item item: Jenkins.getInstance().getItems()){
            if(item instanceof AbstractProject){
                AbstractProject project = (AbstractProject) item;
                ProjectDiskUsageAction action = (ProjectDiskUsageAction) project.getAction(ProjectDiskUsageAction.class);
                diskUsageBuilds += action.getBuildsDiskUsage().get("all");
                diskUsageWorkspaces += action.getAllDiskUsageWorkspace();
                diskUsageJobsWithoutBuilds += action.getAllDiskUsageWithoutBuilds();
                diskUsageLockedBuilds += action.getBuildsDiskUsage().get("locked");
            }
        }
    }
    
    public Long getCashedGlobalBuildsDiskUsage(){
        return diskUsageBuilds;
    }
    
    public Long getCashedGlobalJobsDiskUsage(){
        return (diskUsageBuilds + diskUsageJobsWithoutBuilds);
    }
    
    public Long getCashedGlobalJobsWithoutBuildsDiskUsage(){
        return diskUsageJobsWithoutBuilds;
    }
    
    public Long getCashedGlobalLockedBuildsDiskUsage(){
     return diskUsageLockedBuilds;   
    }
    
    public Long getCashedGlobalWorkspacesDiskUsage(){
        return diskUsageWorkspaces;
    }
    
    public Long getGlobalBuildsDiskUsage(){
        refreshGlobalInformation();
        return diskUsageBuilds;
    }
    
    public Long getGlobalJobsDiskUsage(){
        refreshGlobalInformation();
        return (diskUsageBuilds + diskUsageJobsWithoutBuilds);
    }
    
    public Long getGlobalJobsWithoutBuildsDiskUsage(){
        refreshGlobalInformation();
        return diskUsageJobsWithoutBuilds;
    }
    
    public Long getGlobalWorkspacesDiskUsage(){
        refreshGlobalInformation();
        return diskUsageWorkspaces;
    }
    
    public BuildDiskUsageCalculationThread getBuildsDiskUsageThread(){
        return AperiodicWork.all().get(BuildDiskUsageCalculationThread.class);
    }
    
    public JobWithoutBuildsDiskUsageCalculation getJobsDiskUsageThread(){
        return AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class);
    }
    
    public WorkspaceDiskUsageCalculationThread getWorkspaceDiskUsageThread(){
       return AperiodicWork.all().get(WorkspaceDiskUsageCalculationThread.class); 
    }
    
    /**
     * @return DiskUsage for given project (shortcut for the view). Never null.
     */
    public ProjectDiskUsageAction getDiskUsage(Job project) {
        ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
        return action;
    }
    
    public String getDiskUsageInString(Long size){
        return DiskUsageUtil.getSizeString(size);
    }
    
    //Another shortcut
    public static String getProjectUrl(Job project) {
        return Util.encode(project.getAbsoluteUrl());
    }
    
    /**
     * @return Project list sorted by occupied disk space
     */
    public List getProjectList() {
        refreshGlobalInformation();
        Comparator<AbstractProject> comparator = new Comparator<AbstractProject>() {

            public int compare(AbstractProject o1, AbstractProject o2) {
                
                ProjectDiskUsageAction dua1 = getDiskUsage(o1);
                ProjectDiskUsageAction dua2 = getDiskUsage(o2);
                
                long result = dua2.getJobRootDirDiskUsage() + dua2.getAllDiskUsageWorkspace() - dua1.getJobRootDirDiskUsage() - dua1.getAllDiskUsageWorkspace();
                
                if(result > 0) return 1;
                if(result < 0) return -1;
                return 0;
            }
        };

        List<AbstractProject> projectList = new ArrayList();
        for(Item item: Jenkins.getInstance().getItems()){
            if(item instanceof AbstractProject)
                projectList.add((AbstractProject)item);
        }
        Collections.sort(projectList, comparator);
        
        return projectList;
    }
    
    public void doFilter(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException{
        Date older = DiskUsageUtil.getDate(req.getParameter("older"), req.getParameter("olderUnit"));
        Date younger = DiskUsageUtil.getDate(req.getParameter("younger"), req.getParameter("youngerUnit"));
        req.setAttribute("filter", "filter");
        req.setAttribute("older", older);
        req.setAttribute("younger", younger);
        
        req.getView(this, "index.jelly").forward(req, rsp);     
    }
    
    public void doConfigure(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException{
        req.getView(this, "settings.jelly").forward(req, rsp);
    }
    
    public DiskUsageProjectActionFactory.DescriptorImpl getConfiguration(){
        return DiskUsageProjectActionFactory.DESCRIPTOR;
    }
    
    public Graph getOverallGraph(){
        long maxValue = 0;
        long maxValueWorkspace = 0;
        List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> record = DiskUsageProjectActionFactory.DESCRIPTOR.getHistory();
        //First iteration just to get scale of the y-axis
        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : record){
            if(getConfiguration().getShowFreeSpaceForJobDirectory()){
                maxValue = usage.getAllSpace();
            }
            if(maxValue<=0){
                maxValue = Math.max(maxValue, usage.getJobsDiskUsage());
                maxValueWorkspace = Math.max(maxValueWorkspace, usage.getWorkspacesDiskUsage());
            }
            
        }
        int floor = (int) DiskUsageUtil.getScale(maxValue);
        int floorWorkspace = (int) DiskUsageUtil.getScale(maxValueWorkspace);
        String unit = DiskUsageUtil.getUnitString(floor);
        String unitWorkspace = DiskUsageUtil.getUnitString(floorWorkspace);
        double base = Math.pow(1024, floor);
        double baseWorkspace = Math.pow(1024, floorWorkspace);
        DataSetBuilder<String, Date> dsb = new DataSetBuilder<String, Date>();
        DataSetBuilder<String, Date> dsb2 = new DataSetBuilder<String, Date>();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : record) {
            Date label = usage.getDate();
            if(getConfiguration().getShowFreeSpaceForJobDirectory()){
                dataset.addValue(((Long) usage.getAllSpace()) / base, "space for jobs directory", label);
            }
            dataset.addValue(((Long) usage.getJobsDiskUsage()) / base, "all jobs", label);
            dataset.addValue(((Long) usage.getBuildsDiskUsage()) / base, "all builds", label);
            dsb2.add(((Long) usage.getWorkspacesDiskUsage()) / baseWorkspace, "workspaces", label);     
        }
           return  new DiskUsageGraph(dataset, unit, dsb2.build(), unitWorkspace);
    }  
    
    public void doRecordBuildDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        if(getConfiguration().isCalculationBuildsEnabled() && !getBuildsDiskUsageThread().isExecuting())
            getBuildsDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }
    
    public void doRecordJobsDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        if(getConfiguration().isCalculationJobsEnabled() && !getJobsDiskUsageThread().isExecuting())
            getJobsDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }
    
    public void doRecordWorkspaceDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        if(getConfiguration().isCalculationWorkspaceEnabled() && !getWorkspaceDiskUsageThread().isExecuting())
            getWorkspaceDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }
       
    
    public String getCountIntervalForBuilds(){
        long nextExecution = getBuildsDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
        if(nextExecution<=0) //not scheduled
            nextExecution = getBuildsDiskUsageThread().getRecurrencePeriod();    
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }
    
    public String getCountIntervalForJobs(){
        long nextExecution = getJobsDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
        if(nextExecution<=0) //not scheduled
            nextExecution = getJobsDiskUsageThread().getRecurrencePeriod();
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }
    
    public String getCountIntervalForWorkspaces(){
        long nextExecution = getWorkspaceDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
            if(nextExecution<=0) //not scheduled
            nextExecution = getWorkspaceDiskUsageThread().getRecurrencePeriod();
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }
    
}
