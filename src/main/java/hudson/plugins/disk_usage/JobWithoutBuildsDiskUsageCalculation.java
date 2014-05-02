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
public class JobWithoutBuildsDiskUsageCalculation extends DiskUsageCalculation{
    
    //last scheduled task;
    private static DiskUsageCalculation currentTask;
    
    private static boolean executing;
      
    public JobWithoutBuildsDiskUsageCalculation(){
        super("Calculation of job directories (without builds)");       
    }

    @Override
    public void execute(TaskListener listener) throws IOException, InterruptedException {
         DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        if(plugin.getConfiguration().isCalculationJobsEnabled() && !isExecuting()){
            executing = true;
            try{
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
            catch(Exception e){
                logger.log(Level.WARNING, "Error when recording disk usage for jobs.", e);
            }
            executing=false;
        }
    }

    @Override
    public AperiodicWork getNewInstance() {   
        if(currentTask!=null){
            currentTask.cancel();
        }
        else{
            cancel();
        }
        currentTask = new JobWithoutBuildsDiskUsageCalculation();
        return currentTask;
    }

    @Override
    public CronTab getCronTab() throws ANTLRException {
        String cron = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().getCountIntervalForJobs();
        CronTab tab = new CronTab(cron);
        return tab;
    }

    @Override
    public boolean isExecuting() {
        return executing;
    }

    @Override
    public DiskUsageCalculation getLastTask() {
        return currentTask;
    }

}
