package hudson.plugins.disk_usage;

import antlr.ANTLRException;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.AsyncAperiodicWork;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.scheduler.CronTab;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Level;
import jenkins.model.Jenkins;

/**
 * A Thread responsible for gathering disk usage information
 * 
 * @author dvrzalik
 */
public class BuildDiskUsageCalculationThread extends AsyncAperiodicWork {
    
    public BuildDiskUsageCalculationThread(){
        super("Calculation of builds disk usage");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        List<Item> items = new ArrayList<Item>();
        ItemGroup<? extends Item> itemGroup = Jenkins.getInstance();
        items.addAll(DiskUsageUtil.getAllProjects(itemGroup));

        for (Object item : items) {
            if (item instanceof AbstractProject) {
                AbstractProject project = (AbstractProject) item;
                if (!project.isBuilding()) {

                    List<AbstractBuild> builds = project.getBuilds();
                    for(AbstractBuild build : builds){
                        try {                        
                            DiskUsageUtil.calculateDiskUsageForBuild(build);                        
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Error when recording disk usage for " + project.getName(), ex);
                        }
                    }
                }
            }
        }
    }

    @Override
    public long getRecurrencePeriod() {
        try {
            String cron = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getCountIntervalForBuilds();
            CronTab tab = new CronTab(cron);
            GregorianCalendar now = new GregorianCalendar();
            Calendar nextExecution = tab.ceil(now.getTimeInMillis());
            return now.getTimeInMillis() - nextExecution.getTimeInMillis();           
        } catch (ANTLRException ex) {
            logger.log(Level.SEVERE, null, ex);
            //it should not happen
            return 1000*60*6;
        }
    }

    @Override
    public AperiodicWork getNewInstance() {
        return new BuildDiskUsageCalculationThread();
    }

    
}
