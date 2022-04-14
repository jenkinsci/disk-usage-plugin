/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.AperiodicWork;
import hudson.model.RootAction;
import hudson.plugins.disk_usage.unused.DiskUsageNotUsedDataCalculationThread;
import hudson.util.Graph;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.jfree.data.category.DefaultCategoryDataset;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageJenkinsAction extends DiskUsageItemGroupAction implements RootAction {
    
    public DiskUsageJenkinsAction(){
        super(Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getDiskUsageItemGrouForJenkinsRootAction());
    }
    
    public static DiskUsageJenkinsAction getInstance(){
        List<Action> actions = Jenkins.getInstance().getActions();
        for(Action a : actions){
            if(a instanceof DiskUsageJenkinsAction){
                return (DiskUsageJenkinsAction) a;
            }
        }
        //no action
        //should not happen but if - add action
        DiskUsageJenkinsAction action = new DiskUsageJenkinsAction();
        Jenkins.getInstance().getActions().add(action);
        return action;
    }
    
    @Override
    public String getIconFileName() {
            return "/plugin/disk-usage/icons/disk-usage.svg";
        }

    @Override
    public String getDisplayName() {
        return Messages.DisplayName();
    }

    @Override
    public String getUrlName() {
        return "disk-usage";
    }
    
    public boolean isGraphAvailable(){
        return true;
    }
    
    public Graph getOverallGraph(){
        File jobsDir = new File(Jenkins.getInstance().getRootDir(), "jobs");
        long maxValue = getAllDiskUsage(true);
        if(getConfiguration().getShowFreeSpaceForJobDirectory()){
            maxValue = jobsDir.getTotalSpace();
        }
        long maxValueWorkspace = Math.max(getAllCustomOrNonSlaveWorkspaces(true), getAllDiskUsageWorkspace(true));
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
        dataset.addValue(((Long) getAllDiskUsage(true)) / base, "all jobs", "current");
        dataset.addValue(((Long) getBuildsDiskUsage(null, null, true).get("all")) / base, "all builds", "current");
        datasetW.addValue(((Long) getAllDiskUsageWorkspace(true)) / baseWorkspace, "slave workspaces", "current");
        datasetW.addValue(((Long) getAllCustomOrNonSlaveWorkspaces(true)) / baseWorkspace, "non slave workspaces", "current");
        return  new DiskUsageGraph(dataset, unit, datasetW, unitWorkspace);
    }  

    public DiskUsageProjectActionFactory.DescriptorImpl getConfiguration(){
        return DiskUsageProjectActionFactory.DESCRIPTOR;
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
    
    public DiskUsageNotUsedDataCalculationThread getNotUsedDataDiskUsageThread(){
       return AperiodicWork.all().get(DiskUsageNotUsedDataCalculationThread.class); 
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
    
    public String getCountIntervalForNotUsedData(){
        long nextExecution = getNotUsedDataDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
            if(nextExecution<=0) //not scheduled
            nextExecution = getNotUsedDataDiskUsageThread().getRecurrencePeriod();
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }
    
    public void doRecordBuildDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationBuildsEnabled() && !getBuildsDiskUsageThread().isExecuting())
            getBuildsDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }
    
    public void doRecordJobsDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationJobsEnabled() && !getJobsDiskUsageThread().isExecuting())
            getJobsDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }
    
    public void doRecordWorkspaceDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationWorkspaceEnabled() && !getWorkspaceDiskUsageThread().isExecuting())
            getWorkspaceDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }
    
    public void doRecordNotUsedDataDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationNotUsedDataEnabled() && !getNotUsedDataDiskUsageThread().isExecuting())
            getNotUsedDataDiskUsageThread().doAperiodicRun();
        res.forwardToPreviousPage(req);
    }
}
