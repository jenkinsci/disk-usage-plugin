/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.FilePath;
import hudson.Util;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import hudson.plugins.disk_usage.DiskUsageProperty.DiskUsageDescriptor;
import hudson.plugins.disk_usage.DiskUsageThread.DiskUsageCallable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 *
 * @author lucinka
 */
public class DiskUsageUtil {
    
    public static final String getSizeString(Long size) {
        if (size == null || size <= 0) {
            return "-";
        }

        int floor = (int) getScale(size);
        floor = Math.min(floor, 4);
        double base = Math.pow(1024, floor);
        String unit = getUnitString(floor);

        return Math.round(size / base) + unit;
    }

    public static final double getScale(long number) {
        return Math.floor(Math.log(number) / Math.log(1024));
    }

    public static String getUnitString(int floor) {
        String unit = "";
        switch (floor) {
            case 0:
                unit = "B";
                break;
            case 1:
                unit = "KB";
                break;
            case 2:
                unit = "MB";
                break;
            case 3:
                unit = "GB";
                break;
            case 4:
                unit = "TB";
                break;
        }

        return unit;
    }
    
    public static Long getFileSize(File f, List<File> exceedFiles) throws IOException {
            long size = 0;

            if (f.isDirectory() && !Util.isSymlink(f)) {
            	File[] fileList = f.listFiles();
            	if (fileList != null) for (File child : fileList) {
                    if(exceedFiles.contains(child))
                        continue; //do not count exceeded files
                    if (!Util.isSymlink(child)) size += getFileSize(child, exceedFiles);
                }
                else {
            		LOGGER.info("Failed to list files in " + f.getPath() + " - ignoring");
            	}
            }
            
            return size + f.length();
   }
    
    protected static void calculateDiskUsageForProject(AbstractProject project) throws IOException{
        List<File> exceededFiles = new ArrayList<File>();
        exceededFiles.add(new File(project.getRootDir(),"builds"));
        long buildSize = DiskUsageCallable.getFileSize(project.getRootDir(), exceededFiles);
        DiskUsageDescriptor descriptor = (DiskUsageDescriptor) project.getProperty(DiskUsageProperty.class).getDescriptor();
        Long diskUsageWithoutBuilds = descriptor.getDiskUsageWithoutBuilds();
        boolean update = false;
        	if (( diskUsageWithoutBuilds <= 0 ) ||
        			( Math.abs(diskUsageWithoutBuilds - buildSize) > 1024 )) {
        		descriptor.setDiskUsageWithoutBuilds(buildSize);
        		update = true;
        	}
        if (update) {
        	descriptor.save();
        }
    }


        protected static void calculateDiskUsageForBuild(AbstractBuild build)
            throws IOException {

        //Build disk usage has to be always recalculated to be kept up-to-date 
        //- artifacts might be kept only for the last build and users sometimes delete files manually as well.
        long buildSize = DiskUsageCallable.getFileSize(build.getRootDir(), new ArrayList<File>());
        if (build instanceof MavenModuleSetBuild) {
            Collection<List<MavenBuild>> builds = ((MavenModuleSetBuild) build).getModuleBuilds().values();
            for (List<MavenBuild> mavenBuilds : builds) {
                for (MavenBuild mavenBuild : mavenBuilds) {
                    calculateDiskUsageForBuild(mavenBuild);
                }
            }
        }
        
        BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
        boolean updateBuild = false;
        if (action == null) {
            action = new BuildDiskUsageAction(build, buildSize);
            build.addAction(action);
            updateBuild = true;
        } else {
        	if (( action.diskUsage <= 0 ) ||
        			( Math.abs(action.diskUsage - buildSize) > 1024 )) {
        		action.diskUsage = buildSize;
        		updateBuild = true;
        	}
        }
        if ( updateBuild ) {
        	build.save();
        }
    }
    
    protected static void calculateWorkspaceDiskUsage(AbstractProject project) throws IOException, InterruptedException {
        DiskUsageDescriptor descriptor = (DiskUsageDescriptor) project.getProperty(DiskUsageProperty.class).getDescriptor();
            
        for(Node node: Jenkins.getInstance().getNodes()){
           if(project instanceof TopLevelItem && node.toComputer()!=null && node.toComputer().getChannel()!=null){
               TopLevelItem item = (TopLevelItem) project;
               FilePath workspace = node.getWorkspaceFor(item);
               if(workspace.exists()){
                   Long diskUsage = descriptor.getSlaveWorkspaceUsage().get(node.getNodeName());
                   try{
                        workspace.getChannel().callAsync(new DiskUsageCallable(workspace, new ArrayList<FilePath>())).get(Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getWorkspaceTimeOut(), TimeUnit.MILLISECONDS);             
                   }
                   catch(Exception e){
                       Logger.getLogger(DiskUsageThread.class.getName()).log(Level.WARNING, "Disk usage fails to calculate workspace for job " + project.getDisplayName() + " through channel " + workspace.getChannel(),e);
                   }
                   if(diskUsage!=null && diskUsage>0){
                       descriptor.putSlaveWorkspace(node, diskUsage);
                   }
               }
            }
        }
        descriptor.save();
    }
    
    public static final Logger LOGGER = Logger
    		.getLogger(DiskUsageUtil.class.getName());
}
