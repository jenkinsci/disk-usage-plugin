package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.Plugin;
import hudson.Util;
import hudson.model.*;
import hudson.security.Permission;
import hudson.util.Graph;

import java.io.File;
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

    private Long diskUsageBuilds = 0L;
    private Long diskUsageJobsWithoutBuilds = 0L;
    private Long diskUsageWorkspaces = 0L;
    private Long diskUsageLockedBuilds = 0L;
    private Long diskUsageNonSlaveWorkspaces = 0L;

    public DiskUsagePlugin() {
    }

    public void refreshGlobalInformation() throws IOException {
        diskUsageBuilds = 0l;
        diskUsageWorkspaces = 0l;
        diskUsageJobsWithoutBuilds = 0l;
        diskUsageLockedBuilds = 0l;
        diskUsageNonSlaveWorkspaces = 0l;
        for(Item item: Jenkins.getInstance().getItems()) {
            if(item instanceof AbstractProject) {
                AbstractProject project = (AbstractProject) item;
                ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
                diskUsageBuilds += action.getBuildsDiskUsage().get("all");
                diskUsageWorkspaces += action.getAllDiskUsageWorkspace();
                diskUsageJobsWithoutBuilds += action.getAllDiskUsageWithoutBuilds();
                diskUsageLockedBuilds += action.getBuildsDiskUsage().get("locked");
                diskUsageNonSlaveWorkspaces += action.getAllCustomOrNonSlaveWorkspaces();
            }
        }
    }

    public Long getCashedGlobalBuildsDiskUsage() {
        return diskUsageBuilds;
    }

    public Long getCashedGlobalJobsDiskUsage() {
        return diskUsageBuilds + diskUsageJobsWithoutBuilds;
    }

    public Long getCashedGlobalJobsWithoutBuildsDiskUsage() {
        return diskUsageJobsWithoutBuilds;
    }

    public Long getCashedGlobalLockedBuildsDiskUsage() {
        return diskUsageLockedBuilds;
    }

    public Long getCashedGlobalWorkspacesDiskUsage() {
        return diskUsageWorkspaces;
    }

    public Long getCashedNonSlaveDiskUsageWorkspace() {
        return diskUsageNonSlaveWorkspaces;
    }

    public Long getCashedSlaveDiskUsageWorkspace() {
        return diskUsageWorkspaces - diskUsageNonSlaveWorkspaces;
    }

    public Long getGlobalBuildsDiskUsage() throws IOException {
        refreshGlobalInformation();
        return diskUsageBuilds;
    }

    public Long getGlobalJobsDiskUsage() throws IOException {
        refreshGlobalInformation();
        return diskUsageBuilds + diskUsageJobsWithoutBuilds;
    }

    public Long getGlobalJobsWithoutBuildsDiskUsage() throws IOException {
        refreshGlobalInformation();
        return diskUsageJobsWithoutBuilds;
    }

    public Long getGlobalWorkspacesDiskUsage() throws IOException {
        refreshGlobalInformation();
        return diskUsageWorkspaces;
    }


    public Long getGlobalNonSlaveDiskUsageWorkspace() throws IOException {
        refreshGlobalInformation();
        return diskUsageNonSlaveWorkspaces;
    }

    public Long getGlobalSlaveDiskUsageWorkspace() throws IOException {
        refreshGlobalInformation();
        return diskUsageWorkspaces - diskUsageNonSlaveWorkspaces;
    }

    public BuildDiskUsageCalculationThread getBuildsDiskUsageThread() {
        return AperiodicWork.all().get(BuildDiskUsageCalculationThread.class);
    }

    public JobWithoutBuildsDiskUsageCalculation getJobsDiskUsageThread() {
        return AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class);
    }

    public WorkspaceDiskUsageCalculationThread getWorkspaceDiskUsageThread() {
        return AperiodicWork.all().get(WorkspaceDiskUsageCalculationThread.class);
    }

    /**
     * @return DiskUsage for given project (shortcut for the view). Never null.
     */
    public ProjectDiskUsageAction getDiskUsage(Job project) {
        return project.getAction(ProjectDiskUsageAction.class);
    }

    public String getDiskUsageInString(Long size) {
        return DiskUsageUtil.getSizeString(size);
    }

    /**
     * @return Project list sorted by occupied disk space
     */
    public List getProjectList() throws IOException {
        refreshGlobalInformation();
        Comparator<AbstractProject> comparator = new Comparator<AbstractProject>() {

            public int compare(AbstractProject o1, AbstractProject o2) {

                ProjectDiskUsageAction dua1 = getDiskUsage(o1);
                ProjectDiskUsageAction dua2 = getDiskUsage(o2);
                long result = dua2.getJobRootDirDiskUsage() + dua2.getAllDiskUsageWorkspace() - dua1.getJobRootDirDiskUsage() - dua1.getAllDiskUsageWorkspace();

                if(result > 0) {
                    return 1;
                }
                if(result < 0) {
                    return -1;
                }
                return 0;
            }
        };

        List<AbstractProject> projectList = Jenkins.getInstance().getAllItems(AbstractProject.class);
        Collections.sort(projectList, comparator);

        return projectList;
    }

    public void doFilter(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        Date older = DiskUsageUtil.getDate(req.getParameter("older"), req.getParameter("olderUnit"));
        Date younger = DiskUsageUtil.getDate(req.getParameter("younger"), req.getParameter("youngerUnit"));
        req.setAttribute("filter", "filter");
        req.setAttribute("older", older);
        req.setAttribute("younger", younger);

        req.getView(this, "index.jelly").forward(req, rsp);
    }

    public DiskUsageProjectActionFactory.DescriptorImpl getConfiguration() {
        return DiskUsageProjectActionFactory.DESCRIPTOR;
    }

    public Graph getOverallGraph() {
        File jobsDir = new File(Jenkins.getInstance().getRootDir(), "jobs");
        long maxValue = getCashedGlobalJobsDiskUsage();
        if(getConfiguration().getShowFreeSpaceForJobDirectory()) {
            maxValue = jobsDir.getTotalSpace();
        }
        long maxValueWorkspace = Math.max(diskUsageNonSlaveWorkspaces, getCashedSlaveDiskUsageWorkspace());
        List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> record = DiskUsageProjectActionFactory.DESCRIPTOR.getHistory();
        // First iteration just to get scale of the y-axis
        for(DiskUsageOvearallGraphGenerator.DiskUsageRecord usage: record) {
            if(getConfiguration().getShowFreeSpaceForJobDirectory()) {
                maxValue = Math.max(maxValue, usage.getAllSpace());
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
        for(DiskUsageOvearallGraphGenerator.DiskUsageRecord usage: record) {
            Date label = usage.getDate();
            if(getConfiguration().getShowFreeSpaceForJobDirectory()) {
                dataset.addValue(((Long) usage.getAllSpace()) / base, "space for jobs directory", label);
            }
            dataset.addValue(((Long) usage.getJobsDiskUsage()) / base, "all jobs", label);
            dataset.addValue(((Long) usage.getBuildsDiskUsage()) / base, "all builds", label);
            datasetW.addValue(((Long) usage.getSlaveWorkspacesUsage()) / baseWorkspace, "slave workspaces", label);
            datasetW.addValue(((Long) usage.getNonSlaveWorkspacesUsage()) / baseWorkspace, "non slave workspaces", label);
        }

        // add current state
        if(getConfiguration().getShowFreeSpaceForJobDirectory()) {
            dataset.addValue(((Long) jobsDir.getTotalSpace()) / base, "space for jobs directory", "current");
        }
        dataset.addValue(((Long) getCashedGlobalJobsDiskUsage()) / base, "all jobs", "current");
        dataset.addValue(((Long) getCashedGlobalBuildsDiskUsage()) / base, "all builds", "current");
        datasetW.addValue(((Long) getCashedSlaveDiskUsageWorkspace()) / baseWorkspace, "slave workspaces", "current");
        datasetW.addValue(((Long) getCashedNonSlaveDiskUsageWorkspace()) / baseWorkspace, "non slave workspaces", "current");
        return  new DiskUsageGraph(dataset, unit, datasetW, unitWorkspace);
    }

    public void doRecordBuildDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationBuildsEnabled() && !getBuildsDiskUsageThread().isExecuting()) {
            getBuildsDiskUsageThread().doAperiodicRun();
        }
        res.forwardToPreviousPage(req);
    }

    public void doRecordJobsDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationJobsEnabled() && !getJobsDiskUsageThread().isExecuting()) {
            getJobsDiskUsageThread().doAperiodicRun();
        }
        res.forwardToPreviousPage(req);
    }

    public void doRecordWorkspaceDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationWorkspaceEnabled() && !getWorkspaceDiskUsageThread().isExecuting()) {
            getWorkspaceDiskUsageThread().doAperiodicRun();
        }
        res.forwardToPreviousPage(req);
    }


    public String getCountIntervalForBuilds() {
        long nextExecution = getBuildsDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
        if(nextExecution <= 0) { // not scheduled
            nextExecution = getBuildsDiskUsageThread().getRecurrencePeriod();
        }
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }

    public String getCountIntervalForJobs() {
        long nextExecution = getJobsDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
        if(nextExecution <= 0) { // not scheduled
            nextExecution = getJobsDiskUsageThread().getRecurrencePeriod();
        }
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }

    public String getCountIntervalForWorkspaces() {
        long nextExecution = getWorkspaceDiskUsageThread().scheduledLastInstanceExecutionTime() - System.currentTimeMillis();
        if(nextExecution <= 0) { // not scheduled
            nextExecution = getWorkspaceDiskUsageThread().getRecurrencePeriod();
        }
        return DiskUsageUtil.formatTimeInMilisec(nextExecution);
    }

    public boolean hasAdministrativePermission() {
        return Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER);
    }

}
