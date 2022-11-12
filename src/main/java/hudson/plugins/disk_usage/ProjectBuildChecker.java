/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Lucie Votypkova
 */
public class ProjectBuildChecker {
    
    
    /*
     * Check compatibility of data without loading any build into memory. This causes that it is not possible to check loadability of builds.
     * This check only validy of disk usage data with data on disk
     */
    protected static void checkBuildDataCompatibility(ProjectDiskUsage diskUsage){
        for(DiskUsageBuildInformation info : diskUsage.getBuildDiskUsage(false)){
            File build = new File(diskUsage.job.getBuildDir(),info.getId());
            if(!build.exists()){
                diskUsage.removeBuild(info);
                continue;
            }
            File buildXML = new File(build, "build.xml");
            if(!buildXML.exists()){
                diskUsage.moveToUnloadedBuilds(info);
            }
        }
        for(String buildId : diskUsage.getNotLoadedBuilds()){
            File build = new File(diskUsage.job.getBuildDir(),buildId);
            File buildXML = new File(build, "build.xml");
            //check if all uloaded builds still exists
            if(!build.exists() || !buildXML.exists()){
                diskUsage.removeDeletedNotLoadedBuild(build);
                continue;
            }
        }
    }

    
    public static int getNumberDirectoriesForBuilds(Job project) throws IOException{
        int count = 0;
        File builds = project.getBuildDir();
        for(File file : builds.listFiles()){
            if(!Util.isSymlink(file) && file.exists()){
                count++;
            }
        }
        return count;
    }
    
    /*
     * Check if all builds contained in disk usage data exists and are loadable
     * It forces loading of builds which have build.xml but are not contained into loaded builds data (in the past they were not loadable)
     */
    private static void checkExistenceOfBuilds(ProjectDiskUsage diskUsage){
        //check if directory and build.xml exists for loaded builds
            for(DiskUsageBuildInformation info : diskUsage.getBuildDiskUsage(false)){
                File build = new File(diskUsage.job.getBuildDir(),info.getId());
                if(!build.exists()){
                    diskUsage.removeBuild(info);
                    continue;
                }
                File buildXML = new File(build, "build.xml");
                if(!buildXML.exists()){
                    diskUsage.moveToUnloadedBuilds(info);
                }
            }
            //check if unloaded builds still unloadable
            for(String buildId : diskUsage.getNotLoadedBuilds()){
                File build = new File(diskUsage.job.getBuildDir(),buildId);
                File buildXML = new File(build, "build.xml");
                //check if all uloaded builds still exists
                if(!build.exists() || !buildXML.exists()){
                    diskUsage.removeDeletedNotLoadedBuild(build);
                    continue;
                }
                // directory exists and build.xml too - try to load
                Run run = diskUsage.job.getBuild(buildId);
                if(run instanceof AbstractBuild){
                    diskUsage.moveToLoadedBuilds((AbstractBuild)run, diskUsage.getSizeOfNotLoadedBuild(buildId));
                }
            }
    }
    
    private static List<File> getFilesWhichAreNotTracked(ProjectDiskUsage usage) throws IOException{
        List<File> notTrackedFiles = new ArrayList<File>();
        for(File file : usage.job.getBuildDir().listFiles()){ 
            if(Util.isSymlink(file) || !file.exists()){
                continue;
            }
            if(!usage.containDataForBuildDirectory(file)){
                notTrackedFiles.add(file);
            }
        }
        return notTrackedFiles;
    }
    
    /*
     * Check if data about builds are valid (do not check builds which are conatined in loaded builds and their data on disk seem alright)
     * It causes loading of builds which have build.xml but are not contained into loaded builds data (in the past they were not loadable)
     * Try to load build in directory which is not contained between tracked builds, if there is build.xml
     */
    public static void checkValidityOfBuildData(ProjectDiskUsage diskUsage) throws IOException{
        checkExistenceOfBuilds(diskUsage);
        int directoriesForBuild = getNumberDirectoriesForBuilds(diskUsage.job);
        int countBuildsInDiskUsage =  diskUsage.getCountOfAllBuilds();
        if(directoriesForBuild == countBuildsInDiskUsage){
            return;
        }
        if(directoriesForBuild > countBuildsInDiskUsage){
           List<File> notTrackedFiles = getFilesWhichAreNotTracked(diskUsage);
           for(File file : notTrackedFiles){
               File buildXML = new File(file, "build.xml");
               if(buildXML.exists()){
                   //try to load
                   Run run = diskUsage.job.getBuild(file.getName());
                   if(run instanceof AbstractBuild){
                       diskUsage.addBuild((AbstractBuild)run);
                   }
                   else{
                       diskUsage.addNotLoadedBuild(file, 0L);
                   }
               }
               else{
                   diskUsage.addNotLoadedBuild(file, 0L);
               }
           }
        }
        
    }
    
    /*
     * Check state of build directory and all builds
     * Causes loading all builds into memory
     * This method can takes long time
     */
    public static void valideBuildData(AbstractProject project){
        DiskUsageProperty property = DiskUsageUtil.getDiskUsageProperty(project);
        ProjectDiskUsage usage = property.getDiskUsage();
        //loading all builds
        Iterator<Run> iterator = project.getBuilds().iterator();
        List<String> loadedBuildIds = new ArrayList<String>();
        while(iterator.hasNext()){
            Run run =iterator.next();
            loadedBuildIds.add(run.getId());
            if(run instanceof AbstractBuild){
                AbstractBuild build = (AbstractBuild) run;
                Long size = usage.getSizeOfNotLoadedBuild(build.getId());
                if(size==null || !(size>0)){
                    size = usage.getSizeOfNotLoadedBuild(String.valueOf(build.getNumber()));
                }
                if(size!=null){
                    usage.moveToLoadedBuilds(build, size);
                    continue;
                }
                DiskUsageBuildInformation information = usage.getDiskUsageBuildInformation(build.getId());
                if(information==null){
                    information = usage.getDiskUsageBuildInformation(build.getNumber());
                }
                if(information==null){
                    usage.addBuild(build);
                }
                else{
                    //update info about locking
                    information.setLockState(build.isKeepLog());
                }
            }
        }
        if(loadedBuildIds.size() != usage.countLoadedBuilds()){
            for(DiskUsageBuildInformation info : usage.getBuildDiskUsage(false)){
                if(!loadedBuildIds.contains(info.getId())){
                    usage.moveToUnloadedBuilds(info);
                }
            }
        }
        for(String unloadedBuildId : usage.getNotLoadedBuilds()){
            File file = new File(usage.job.getBuildDir(),unloadedBuildId);
            if(!file.exists()){
                usage.removeDeletedNotLoadedBuild(file);
            }
        }
        
        if(project instanceof ItemGroup){
            ItemGroup group = (ItemGroup) project;
            for(Item item : (Collection<Item>) group.getItems()){
                if(item instanceof AbstractProject){
                   valideBuildData((AbstractProject)item); 
                }
                
            }
        }
        usage.save();
    }
    
}
