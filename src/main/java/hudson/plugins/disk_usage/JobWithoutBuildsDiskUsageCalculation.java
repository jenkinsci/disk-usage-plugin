/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import antlr.ANTLRException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.scheduler.CronTab;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import jenkins.model.Jenkins;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class JobWithoutBuildsDiskUsageCalculation extends DiskUsageCalculation {

    // last scheduled task;
    private static DiskUsageCalculation currentTask;


    public JobWithoutBuildsDiskUsageCalculation() {
        super("Calculation of job directories (without builds)");
    }

    @Override
    public void execute(TaskListener listener) throws IOException, InterruptedException {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if (plugin == null) {
            return;
        }
        if(!isCancelled() && startExecution()) {
            try {
                ItemGroup<? extends Item> itemGroup = Jenkins.get();
                List<Item> items = new ArrayList<>(DiskUsageUtil.getAllProjects(itemGroup));

                for(Object item: items) {
                    if(item instanceof AbstractProject) {
                        AbstractProject<?,?> project = (AbstractProject<?,?>) item;
                        // do not count building project
                        if(project.isBuilding()) {
                            continue;
                        }
                        try {
                            DiskUsageUtil.calculateDiskUsageForProject(project);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Error when recording disk usage for " + project.getName(), ex);
                        }
                    }
                }
                if(plugin.getConfiguration().warnAboutAllJobsExceetedSize()) {
                    DiskUsageUtil.controlAllJobsExceedSize();
                }
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "Error when recording disk usage for jobs.", e);
            }
        }
        else {
            if(plugin.getConfiguration().isCalculationJobsEnabled()) {
                logger.log(Level.FINER, "Calculation of jobs is already in progress.");
            }
            else {
                logger.log(Level.FINER, "Calculation of jobs is disabled.");
            }
        }
    }

    @Override
    @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public AperiodicWork getNewInstance() {
        if(currentTask != null) {
            currentTask.cancel();
        }
        else {
            cancel();
        }
        currentTask = new JobWithoutBuildsDiskUsageCalculation();
        return currentTask;
    }

    @Override
    public CronTab getCronTab() throws ANTLRException {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if (plugin == null) {
            return null;
        }
        String cron = plugin.getConfiguration().getCountIntervalForJobs();
        return new CronTab(cron);
    }

    @Override
    public DiskUsageCalculation getLastTask() {
        return currentTask;
    }

    private boolean startExecution() {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if(plugin == null || !plugin.getConfiguration().isCalculationJobsEnabled()) {
            return false;
        }
        return !isExecutingMoreThenOneTimes();
    }

}
