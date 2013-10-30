/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.AsyncAperiodicWork;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.scheduler.CronTab;
import hudson.triggers.Trigger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Level;
import jenkins.model.Jenkins;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class JobWithoutBuildsDiskUsageCalculation extends AsyncAperiodicWork{
    
    //last scheduled task;
    private static JobWithoutBuildsDiskUsageCalculation currentTask;
      
    public JobWithoutBuildsDiskUsageCalculation(){
        super("Calculation of job directories (without builds)");       
    }
    
    public boolean isExecuting(){
        for(Thread t: Thread.getAllStackTraces().keySet()){
            if((name +" thread").equals(t.getName()))
                return true;
        }
        return false;
    }
    
    public void reschedule(){
        if(currentTask==null){
            cancel();
        }
        else{
            currentTask.cancel();   
        }
        Trigger.timer.purge();
        Trigger.timer.schedule(getNewInstance(), getRecurrencePeriod());
    }

    @Override
    public void execute(TaskListener listener) throws IOException, InterruptedException {
         DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        if(plugin.getConfiguration().isCalculationJobsEnabled() && !isExecuting()){
            List<Item> items = new ArrayList<Item>();
            ItemGroup<? extends Item> itemGroup = Jenkins.getInstance();
            items.addAll(DiskUsageUtil.getAllProjects(itemGroup));

            for (Object item : items) {
                if (item instanceof AbstractProject) {
                    AbstractProject project = (AbstractProject) item;
                    try{
                        DiskUsageUtil.calculateDiskUsageForProject(project);
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Error when recording disk usage for " + project.getName(), ex);
                    }               
                }
            }
            if(plugin.getConfiguration().warnAboutAllJobsExceetedSize()){
                DiskUsageUtil.controlAllJobsExceedSize();
            }
        }
    }
    
    @Override
    public long getInitialDelay(){
        return getRecurrencePeriod();
    }  
    
    @Override
    public long scheduledExecutionTime(){
        if(currentTask==null || currentTask==this)
            return super.scheduledExecutionTime();
        return currentTask.scheduledExecutionTime();
    }
 
    public long getRecurrencePeriod() {
        try {
            String cron = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().getCountIntervalForJobs();
            CronTab tab = new CronTab(cron);
            GregorianCalendar now = new GregorianCalendar();
            Calendar nextExecution = tab.ceil(now.getTimeInMillis()); 
            long period = nextExecution.getTimeInMillis() - now.getTimeInMillis() + 60000l; // add delay
            return period;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
            //it should not happen
            return 1000*60*6;
        }
    }

        @Override
    public AperiodicWork getNewInstance() {        
        currentTask =  new JobWithoutBuildsDiskUsageCalculation();
        return currentTask;
    }

}
