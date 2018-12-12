/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.unused;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.plugins.disk_usage.*;
import hudson.scheduler.CronTab;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import jenkins.model.Jenkins;



/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageNotUsedDataCalculationThread extends DiskUsageCalculation {
    
    //last scheduled task;
    private static DiskUsageCalculation currentTask;
    
    public DiskUsageNotUsedDataCalculationThread(){        
        super("Calculation of not used data"); 
    } 

    @Override
    public DiskUsageCalculation getLastTask() {
        synchronized (DiskUsageNotUsedDataCalculationThread.class) {
            return currentTask;
        }
    }

    public CronTab getCronTab() throws ANTLRException{
        if(!DiskUsageProjectActionFactory.DESCRIPTOR.isCalculationNotUsedDataEnabled()){
            return new CronTab("0 1 * * 7");
        }
        String cron = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().getCountIntervalForNotUsedData();
        CronTab tab = new CronTab(cron);
        return tab;
    }

    @Override
    protected void execute(TaskListener tl) throws IOException, InterruptedException {
        if(!isCancelled() && startExecution()){
            for(Item item : Jenkins.getInstance().getItems()){
                if(item instanceof AbstractProject){
                    ProjectBuildChecker.valideBuildData((AbstractProject)item);
                }
            }
        }
        DiskUsageUtil.calculateDiskUsageNotLoadedJobs(Jenkins.getInstance());
        DiskUsageJenkinsAction.getInstance().actualizeCachedNotLoadedJobsData();
//        plugin.getNotUsedDataDiskUsage().save();
    }
    
    

    @Override
    public AperiodicWork getNewInstance() {
        synchronized (DiskUsageNotUsedDataCalculationThread.class) {
            if (currentTask != null) {
                currentTask.cancel();
            } else {
                cancel();
            }
            currentTask = new DiskUsageNotUsedDataCalculationThread();
            return currentTask;
        }
    }
    
    private boolean startExecution(){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        if(!plugin.getConfiguration().isCalculationNotUsedDataEnabled())
          return false;
        return !isExecutingMoreThenOneTimes();
    }
    
    
    
}
