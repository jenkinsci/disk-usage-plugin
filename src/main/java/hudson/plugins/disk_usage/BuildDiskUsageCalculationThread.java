package hudson.plugins.disk_usage;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.*;
import hudson.scheduler.CronTab;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;


/**
 * A Thread responsible for gathering disk usage information
 * 
 * @author dvrzalik
 */
@Extension
public class BuildDiskUsageCalculationThread extends DiskUsageCalculation {   
    
    //last scheduled task;
    private static DiskUsageCalculation currentTask;
      
    public BuildDiskUsageCalculationThread(){        
        super("Calculation of builds disk usage"); 
    }   
    
    @Override
    public void execute(TaskListener listener) throws IOException, InterruptedException {
        if(!isCancelled() && startExecution()){
            try{
                List<Item> items = new ArrayList<Item>();
                ItemGroup<? extends Item> itemGroup = Jenkins.getInstance();
                items.addAll(DiskUsageUtil.getAllProjects(itemGroup));
                for (Object item : items) {
                    if (item instanceof AbstractProject) {
                        AbstractProject project = (AbstractProject) item;
                      //  if (!project.isBuilding()) {
                            DiskUsageProperty property = DiskUsageUtil.getDiskUsageProperty(project);
                            ProjectDiskUsage diskUsage = property.getProjectDiskUsage();
                            if(DiskUsageProjectActionFactory.DESCRIPTOR.getConfiguration().getJobConfiguration().getBuildConfiguration().isInfoAboutBuildsExact()){
                              for(DiskUsageBuildInformation information: diskUsage.getBuildDiskUsage(true)){
                                Map<Integer,AbstractBuild> loadedBuilds = project._getRuns().getLoadedBuilds();
                                AbstractBuild build = loadedBuilds.get(information.getNumber());
                                //do not calculat builds in progress
                                if(build!=null && build.isBuilding()){
                                    continue;
                                }
                                try{
                                    DiskUsageUtil.calculateDiskUsageForBuild(information.getId(), project);
                                }
                                catch(Exception e){
                                    logger.log(Level.WARNING, "Error when recording disk usage for " + project.getName(), e);
                                }
                              }

                            }
                            else{
                                File buildDir = project.getBuildDir();
                                for(File build : buildDir.listFiles()){
                                    if(DiskUsageUtil.isBuildDirName(build)){
                                        DiskUsageBuildInformation info = diskUsage.getDiskUsageBuildInformation(build.getName());
                                        if(info==null){
                                            info = new DiskUsageBuildInformation(build.getName());
                                            diskUsage.addNotExactBuildInformation(info);

                                        }
                                        Map<Integer,Run> loadedBuilds = project._getRuns().getLoadedBuilds();
                                        for(Run run : loadedBuilds.values()){
                                            if(run.getRootDir().getAbsolutePath().equals(build.getAbsolutePath())){
                                                info.obtainInformation(run);
                                            }
                                        }
                                        DiskUsageUtil.calculateDiskUsageForBuild(build.getName(), project);
                                    }
                                    else{
                                        diskUsage.addNotLoadedBuild(build,DiskUsageUtil.getFileSize(build, Collections.EMPTY_LIST));
                                    }
                                }
                            }
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error when recording disk usage for builds", ex);
            }
            DiskUsageJenkinsAction.getInstance().actualizeCashedBuildsData();
        }
        else{
            DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
            if(plugin.getConfiguration().isCalculationBuildsEnabled()){
                logger.log(Level.FINER, "Calculation of builds is already in progress.");
            }
            else{
                logger.log(Level.FINER, "Calculation of builds is disabled.");
            }
        }
    }
    
    public CronTab getCronTab() throws ANTLRException{
        if(!DiskUsageProjectActionFactory.DESCRIPTOR.isCalculationBuildsEnabled()){
            return new CronTab("0 1 * * 7");
        }
        String cron = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().getCountIntervalForBuilds();
        CronTab tab = new CronTab(cron);
        return tab;
    }   

    @Override
    public AperiodicWork getNewInstance() {   
        if(currentTask!=null){
            currentTask.cancel();
        }
        else{
            cancel();
        }
        currentTask = new BuildDiskUsageCalculationThread();
        return currentTask;
    }

    @Override
    public DiskUsageCalculation getLastTask() {
        return currentTask;
    }
    
    private boolean startExecution(){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        if(!plugin.getConfiguration().isCalculationBuildsEnabled()) {
            return false;
        }
        return !isExecutingMoreThenOneTimes();
    }
    
}
