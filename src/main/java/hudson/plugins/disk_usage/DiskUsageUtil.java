/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import hudson.remoting.Callable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        List<AbstractBuild> builds = project.getBuilds();
        for(AbstractBuild build : builds){
            exceededFiles.add(build.getRootDir());
        }
        if(project instanceof ItemGroup){
            List<AbstractProject> projects = getAllProjects((ItemGroup) project);
            for(AbstractProject p: projects){
                    exceededFiles.add(p.getRootDir());
            }
        }
        long buildSize = DiskUsageUtil.getFileSize(project.getRootDir(), exceededFiles);
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property==null){
            property = new DiskUsageProperty();
            project.addProperty(property);
        }
        Long diskUsageWithoutBuilds = property.getDiskUsageWithoutBuilds();
        boolean update = false;
        	if (( diskUsageWithoutBuilds <= 0 ) ||
        			( Math.abs(diskUsageWithoutBuilds - buildSize) > 1024 )) {
        		property.setDiskUsageWithoutBuilds(buildSize);
        		update = true;
        	}
        if (update) {
        	project.save();
        }
    }


        protected static void calculateDiskUsageForBuild(AbstractBuild build)
            throws IOException {

        //Build disk usage has to be always recalculated to be kept up-to-date 
        //- artifacts might be kept only for the last build and users sometimes delete files manually as well.
        long buildSize = DiskUsageUtil.getFileSize(build.getRootDir(), new ArrayList<File>());
//        if (build instanceof MavenModuleSetBuild) {
//            Collection<List<MavenBuild>> builds = ((MavenModuleSetBuild) build).getModuleBuilds().values();
//            for (List<MavenBuild> mavenBuilds : builds) {
//                for (MavenBuild mavenBuild : mavenBuilds) {
//                    calculateDiskUsageForBuild(mavenBuild);
//                }
//            }
//        }
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
        
    protected static Long calculateWorkspaceDiskUsageForPath(FilePath workspace, ArrayList<FilePath> exceeded) throws IOException, InterruptedException{
        Long diskUsage = 0l;
        if(workspace.exists()){
            try{
                diskUsage = workspace.getChannel().callAsync(new DiskUsageCallable(workspace, exceeded)).get(Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getWorkspaceTimeOut(), TimeUnit.MILLISECONDS);             
            }
            catch(Exception e){
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage fails to calculate workspace for file path " + workspace.getRemote() + " through channel " + workspace.getChannel(),e);
            }
        }
        return diskUsage;
    }
    
    protected static void calculateWorkspaceDiskUsage(AbstractProject project) throws IOException, InterruptedException {
        DiskUsageProperty property =  (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property==null){
            property = new DiskUsageProperty();
            project.addProperty(property);
        }
        property.checkWorkspaces();
        for(String nodeName: property.getSlaveWorkspaceUsage().keySet()){
            Node node = Jenkins.getInstance().getNode(nodeName);
            if(node.toComputer()!=null && node.toComputer().getChannel()!=null){
                for(String projectWorkspace: property.getSlaveWorkspaceUsage().get(nodeName).keySet()){
                    FilePath workspace = new FilePath(node.toComputer().getChannel(), projectWorkspace);
                    if(workspace.exists()){
                        Long diskUsage = property.getSlaveWorkspaceUsage().get(node.getNodeName()).get(workspace.getRemote());
                        ArrayList<FilePath> exceededFiles = new ArrayList<FilePath>();
                        if(project instanceof ItemGroup){
                            List<AbstractProject> projects = getAllProjects((ItemGroup) project);
                            for(AbstractProject p: projects){
                                DiskUsageProperty prop = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                                if(prop==null){
                                    prop = new DiskUsageProperty();
                                    p.addProperty(prop);
                                }
                                prop.checkWorkspaces();
                                Map<String,Long> paths = prop.getSlaveWorkspaceUsage().get(node.getNodeName());
                                if(paths!=null && !paths.isEmpty()){
                                    for(String path: paths.keySet()){
                                        exceededFiles.add(new FilePath(node.getChannel(),path));
                                    }
                                }
                            }
                        }
                        diskUsage = calculateWorkspaceDiskUsageForPath(workspace, exceededFiles);
                        if(diskUsage!=null && diskUsage>0){
                            property.putSlaveWorkspaceSize(node, workspace.getRemote(), diskUsage);
                        }
                    }
                }
            }
        }
        project.save();
    }
    
    public static List<AbstractProject> getAllProjects(ItemGroup<? extends Item> itemGroup) {
        List<AbstractProject> items = new ArrayList<AbstractProject>();
        for (Item item : itemGroup.getItems()) {
            if(item instanceof AbstractProject){
                items.add((AbstractProject)item);
            }
            if (item instanceof ItemGroup) {
                items.addAll(getAllProjects((ItemGroup) item));
            }
        }
        return items;
    }

    /**
     * A {@link Callable} which computes disk usage of remote file object
     */
    public static class DiskUsageCallable implements Callable<Long, IOException> {

    	public static final Logger LOGGER = Logger
    		.getLogger(DiskUsageCallable.class.getName());

        private FilePath path;
        private List<FilePath> exceedFilesPath;

        public DiskUsageCallable(FilePath filePath, List<FilePath> exceedFilesPath) {
            this.path = filePath;
            this.exceedFilesPath = exceedFilesPath;
        }

        public Long call() throws IOException {
            File f = new File(path.getRemote());
            List<File> exceeded = new ArrayList<File>();
            for(FilePath file: exceedFilesPath){
                exceeded.add(new File(file.getRemote()));
            }
            return DiskUsageUtil.getFileSize(f, exceeded);
        }
       
    }
    
    public static final Logger LOGGER = Logger
    		.getLogger(DiskUsageUtil.class.getName());
}
