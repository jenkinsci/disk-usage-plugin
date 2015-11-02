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
import hudson.plugins.disk_usage.DiskUsageCalculation;
import hudson.plugins.disk_usage.DiskUsagePlugin;
import hudson.plugins.disk_usage.DiskUsageUtil;
import hudson.plugins.disk_usage.ProjectBuildChecker;
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
        return currentTask;
    }

    public CronTab getCronTab() throws ANTLRException{
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
//        plugin.getNotUsedDataDiskUsage().save();
    }
    
    

    @Override
    public AperiodicWork getNewInstance() {   
        if(currentTask!=null){
            currentTask.cancel();
        }
        else{
            cancel();
        }
        currentTask = new DiskUsageNotUsedDataCalculationThread();
        return currentTask;
    }
    
    private boolean startExecution(){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        if(!plugin.getConfiguration().isCalculationNotUsedDataEnabled())
          return false;
        return !isExecutingMoreThenOneTimes();
    }
    
    
    
}
