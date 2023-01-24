/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import antlr.ANTLRException;
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
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        if(!isCancelled() && startExecution()) {
            try {
                List<Item> items = new ArrayList<Item>();
                ItemGroup<? extends Item> itemGroup = Jenkins.getInstance();
                items.addAll(DiskUsageUtil.getAllProjects(itemGroup));
                for(Object item: items) {
                    if(item instanceof AbstractProject) {
                        AbstractProject project = (AbstractProject) item;
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
    public CronTab getCronTab() throws ANTLRException {
        String cron = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().getCountIntervalForWorkspaces();
        CronTab tab = new CronTab(cron);
        return tab;
    }

    @Override
    public DiskUsageCalculation getLastTask() {
        return currentTask;
    }

    private boolean startExecution() {
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        if(!plugin.getConfiguration().isCalculationWorkspaceEnabled()) {
            return false;
        }
        return !isExecutingMoreThenOneTimes();
    }

}
