package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.Plugin;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.Item;
import hudson.model.Job;
import hudson.util.Graph;
import java.io.File;
import java.io.IOException;
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
 * Entry point of the plugin.
 *
 * @author dvrzalik
 */
@Extension
public class DiskUsagePlugin extends Plugin {

    private Long diskUsageBuilds = 0L;
    private Long diskUsageJobsWithoutBuilds = 0L;
    private Long diskUsageWorkspaces = 0L;
    private Long diskUsageLockedBuilds = 0L;
    private Long diskUsageNonAgentWorkspaces = 0L;

    public DiskUsagePlugin() {
    }

    public void refreshGlobalInformation() throws IOException {
        diskUsageBuilds = 0L;
        diskUsageWorkspaces = 0L;
        diskUsageJobsWithoutBuilds = 0L;
        diskUsageLockedBuilds = 0L;
        diskUsageNonAgentWorkspaces = 0L;
        for(Item item: Jenkins.get().getItems()) {
            if(item instanceof AbstractProject) {
                AbstractProject<?,?> project = (AbstractProject<?,?>) item;
                ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
                diskUsageBuilds += action.getBuildsDiskUsage().get("all");
                diskUsageWorkspaces += action.getAllDiskUsageWorkspace();
                diskUsageJobsWithoutBuilds += action.getAllDiskUsageWithoutBuilds();
                diskUsageLockedBuilds += action.getBuildsDiskUsage().get("locked");
                diskUsageNonAgentWorkspaces += action.getAllCustomOrNonAgentWorkspaces();
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

    @Deprecated(forRemoval = true)
    public Long getCashedNonSlaveDiskUsageWorkspace() {
        return diskUsageNonAgentWorkspaces;
    }

    public Long getCashedNonAgentDiskUsageWorkspace() {
        return diskUsageNonAgentWorkspaces;
    }

    @Deprecated(forRemoval = true)
    public Long getCashedSlaveDiskUsageWorkspace() {
        return getCashedAgentDiskUsageWorkspace();
    }

    public Long getCashedAgentDiskUsageWorkspace() {
        return diskUsageWorkspaces - diskUsageNonAgentWorkspaces;
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

    @Deprecated(forRemoval = true)
    public Long getGlobalNonSlaveDiskUsageWorkspace() throws IOException {
        return getGlobalNonAgentDiskUsageWorkspace();
    }

    public Long getGlobalNonAgentDiskUsageWorkspace() throws IOException {
        refreshGlobalInformation();
        return diskUsageNonAgentWorkspaces;
    }

    @Deprecated(forRemoval = true)
    public Long getGlobalSlaveDiskUsageWorkspace() throws IOException {
        return getGlobalAgentDiskUsageWorkspace();
    }

    public Long getGlobalAgentDiskUsageWorkspace() throws IOException {
        refreshGlobalInformation();
        return diskUsageWorkspaces - diskUsageNonAgentWorkspaces;
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
    public ProjectDiskUsageAction getDiskUsage(Job<?,?> project) {
        return project.getAction(ProjectDiskUsageAction.class);
    }

    public String getDiskUsageInString(Long size) {
        return DiskUsageUtil.getSizeString(size);
    }

    /**
     * @return Project list sorted by occupied disk space
     */
    public List<?> getProjectList() throws IOException {
        refreshGlobalInformation();
        Comparator<AbstractProject> comparator = (o1, o2) -> {

            ProjectDiskUsageAction dua1 = getDiskUsage(o1);
            ProjectDiskUsageAction dua2 = getDiskUsage(o2);
            long result = dua2.getJobRootDirDiskUsage() + dua2.getAllDiskUsageWorkspace() - dua1.getJobRootDirDiskUsage() -
                dua1.getAllDiskUsageWorkspace();

            if (result > 0) {
                return 1;
            }
            if (result < 0) {
                return -1;
            }
            return 0;
        };

        List<AbstractProject> projectList = Jenkins.get().getAllItems(AbstractProject.class);
        projectList.sort(comparator);

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
        File jobsDir = new File(Jenkins.get().getRootDir(), "jobs");
        long maxValue = getCashedGlobalJobsDiskUsage();
        if(getConfiguration().getShowFreeSpaceForJobDirectory()) {
            maxValue = jobsDir.getTotalSpace();
        }
        long maxValueWorkspace = Math.max(diskUsageNonAgentWorkspaces, getCashedAgentDiskUsageWorkspace());
        List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> record = DiskUsageProjectActionFactory.DESCRIPTOR.getHistory();
        // First iteration just to get scale of the y-axis
        for(DiskUsageOvearallGraphGenerator.DiskUsageRecord usage: record) {
            if(getConfiguration().getShowFreeSpaceForJobDirectory()) {
                maxValue = Math.max(maxValue, usage.getAllSpace());
            }
            maxValue = Math.max(maxValue, usage.getJobsDiskUsage());
            maxValueWorkspace = Math.max(maxValueWorkspace, usage.getAgentWorkspacesUsage());
            maxValueWorkspace = Math.max(maxValueWorkspace, usage.getNonAgentWorkspacesUsage());
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
                dataset.addValue(usage.getAllSpace() / base, "space for jobs directory", label);
            }
            dataset.addValue(usage.getJobsDiskUsage() / base, "all jobs", label);
            dataset.addValue(usage.getBuildsDiskUsage() / base, "all builds", label);
            datasetW.addValue(usage.getAgentWorkspacesUsage() / baseWorkspace, "agent workspaces", label);
            datasetW.addValue(usage.getNonAgentWorkspacesUsage() / baseWorkspace, "non agent workspaces", label);
        }

        // add current state
        if(getConfiguration().getShowFreeSpaceForJobDirectory()) {
            dataset.addValue(jobsDir.getTotalSpace() / base, "space for jobs directory", "current");
        }
        dataset.addValue(getCashedGlobalJobsDiskUsage() / base, "all jobs", "current");
        dataset.addValue(getCashedGlobalBuildsDiskUsage() / base, "all builds", "current");
        datasetW.addValue(getCashedAgentDiskUsageWorkspace() / baseWorkspace, "agent workspaces", "current");
        datasetW.addValue(getCashedNonAgentDiskUsageWorkspace() / baseWorkspace, "non agent workspaces", "current");
        return  new DiskUsageGraph(dataset, unit, datasetW, unitWorkspace);
    }

    public void doRecordBuildDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationBuildsEnabled() && !getBuildsDiskUsageThread().isExecuting()) {
            getBuildsDiskUsageThread().doAperiodicRun();
        }
        res.forwardToPreviousPage(req);
    }

    public void doRecordJobsDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if(getConfiguration().isCalculationJobsEnabled() && !getJobsDiskUsageThread().isExecuting()) {
            getJobsDiskUsageThread().doAperiodicRun();
        }
        res.forwardToPreviousPage(req);
    }

    public void doRecordWorkspaceDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
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
        return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
    }

}
