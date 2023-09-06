/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

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
public class WorkspaceDiskUsageCalculationThread extends DiskUsageCalculation {

    // last scheduled task;
    private static DiskUsageCalculation currentTask;

    public WorkspaceDiskUsageCalculationThread() {
        super("Calculation of workspace usage");
    }

    @Override
    public void execute(TaskListener listener) throws IOException, InterruptedException {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if(!isCancelled() && startExecution()) {
            try {
                ItemGroup<? extends Item> itemGroup = Jenkins.get();
                List<Item> items = new ArrayList<>(DiskUsageUtil.getAllProjects(itemGroup));
                for(Object item: items) {
                    if(item instanceof AbstractProject) {
                        AbstractProject<?,?> project = (AbstractProject<?,?>) item;
                        // do not count workspace for running project
                        if(project.isBuilding()) {
                            continue;
                        }
                        try {
                            DiskUsageUtil.calculateWorkspaceDiskUsage(project);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Error when recording disk usage for " + project.getName(), ex);
                        }
                    }
                }
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "Error when recording disk usage for workspaces.", e);
            }
        }
        else {
            if(plugin.getConfiguration().isCalculationWorkspaceEnabled()) {
                logger.log(Level.FINER, "Calculation of workspace is already in progress.");
            }
            else {
                logger.log(Level.FINER, "Calculation of workspace is disabled.");
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
        currentTask = new WorkspaceDiskUsageCalculationThread();
        return currentTask;
    }

    @Override
    public CronTab getCronTab() {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if (plugin == null) {
            return null;
        }
        String cron = plugin.getConfiguration().getCountIntervalForWorkspaces();
        return new CronTab(cron);
    }

    @Override
    public DiskUsageCalculation getLastTask() {
        return currentTask;
    }

    private boolean startExecution() {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if(plugin == null || !plugin.getConfiguration().isCalculationWorkspaceEnabled()) {
            return false;
        }
        return !isExecutingMoreThenOneTimes();
    }

}
