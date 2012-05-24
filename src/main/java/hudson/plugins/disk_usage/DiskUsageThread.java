package hudson.plugins.disk_usage;

import hudson.FilePath;
import hudson.Util;
import hudson.Extension;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.remoting.Callable;
import java.io.File;
import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Thread responsible for gathering disk usage information
 * 
 * @author dvrzalik
 */
@Extension
public class DiskUsageThread extends AsyncPeriodicWork {
    //trigger disk usage thread each 6 hours
    public static final int COUNT_INTERVAL_MINUTES = 60*6;


    public DiskUsageThread() {
        super("Project disk usage");
    }

    public long getRecurrencePeriod() {
        return 1000*60*COUNT_INTERVAL_MINUTES;
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        List<Item> items = new ArrayList<Item>();
        ItemGroup<? extends Item> itemGroup = Hudson.getInstance();
        addAllItems(itemGroup, items);

        for (Object item : items) {
            if (item instanceof AbstractProject) {
                AbstractProject project = (AbstractProject) item;

                //well, this is not absolutely thread-safe, but in the worst case we get invalid result for one build
                //(which will be rewritten next time)
                if (!project.isBuilding()) {

                    List<AbstractBuild> builds = project.getBuilds();
                    Iterator<AbstractBuild> buildIterator = builds.iterator();
                    try {

                        while (buildIterator.hasNext()) {
                            calculateDiskUsageForBuild(buildIterator.next());
                        }

                        //Assign workspace size to the last build
                        calculateWorkspaceDiskUsage(project);

                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Error when recording disk usage for " + project.getName(), ex);
                    }
                }
            }
        }
    }

    /**
     * Recursively add items form itemGroup
     */
    public List<Item> addAllItems(ItemGroup<? extends Item> itemGroup, List<Item> items) {
        for (Item item : itemGroup.getItems()) {
            if (item instanceof MatrixProject) {
                items.add(item);
            } else if (item instanceof ItemGroup) {
                addAllItems((ItemGroup) item, items);
            } else {
                items.add(item);
            }
        }
        return items;
    }

    private static void calculateDiskUsageForBuild(AbstractBuild build)
            throws IOException {

        //Build disk usage has to be always recalculated to be kept up-to-date 
        //- artifacts might be kept only for the last build and users sometimes delete files manually as well.
        long buildSize = DiskUsageCallable.getFileSize(build.getRootDir());
        BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
        boolean updateBuild = false;
        if (action == null) {
            action = new BuildDiskUsageAction(build, 0, buildSize);
            build.addAction(action);
            updateBuild = true;
        } else {
        	if (( action.diskUsage.buildUsage <= 0 ) ||
        			( Math.abs(action.diskUsage.buildUsage - buildSize) > 1024 )) {
        		action.diskUsage.buildUsage = buildSize;
        		updateBuild = true;
        	}
        }
        if ( updateBuild ) {
        	build.save();
        }
    }
    
    private static void calculateWorkspaceDiskUsage(AbstractProject project) throws IOException, InterruptedException {
        AbstractBuild lastBuild = (AbstractBuild) project.getLastBuild();
        if (lastBuild != null) {
            BuildDiskUsageAction bdua = lastBuild.getAction(BuildDiskUsageAction.class);
            //also recalculate workspace - deleting workspace by e.g. scripts is also quite common
            boolean updateWs = false;
            if (bdua == null) {
                bdua = new BuildDiskUsageAction(lastBuild, 0, 0);
                lastBuild.addAction(bdua);
                updateWs = true;
            }
            FilePath workspace = project.getSomeWorkspace();
            //slave might be offline...or have been deleted - set to 0
            if (workspace != null) {
            	long oldWsUsage = bdua.diskUsage.wsUsage;
                bdua.diskUsage.wsUsage = workspace.act(new DiskUsageCallable(workspace));
                if (Math.abs(bdua.diskUsage.wsUsage - oldWsUsage) > 1024 ) {
                	updateWs = true;
                }
            }
            else{
            	bdua.diskUsage.wsUsage = 0; //workspace have been delete or is not reachable
            }
            if(updateWs){
            	lastBuild.save();
            }
        }
    }

    /**
     * A {@link Callable} which computes disk usage of remote file object
     */
    public static class DiskUsageCallable implements Callable<Long, IOException> {

    	public static final Logger LOGGER = Logger
    		.getLogger(DiskUsageCallable.class.getName());

        private FilePath path;

        public DiskUsageCallable(FilePath filePath) {
            this.path = filePath;
        }

        public Long call() throws IOException {
            File f = new File(path.getRemote());
            return getFileSize(f);
        }

        public static Long getFileSize(File f) throws IOException {
            long size = 0;

            if (f.isDirectory() && !Util.isSymlink(f)) {
            	File[] fileList = f.listFiles();
            	if (fileList != null) {
                    for (File child : fileList) {
                        size += getFileSize(child);
                    }
            	} else {
            		LOGGER.info("Failed to list files in " + f.getPath() + " - ignoring");
            	}
            }
            
            return size + f.length();
        }
    }
}
