package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import hudson.plugins.disk_usage.DiskUsageProperty.DiskUsageDescriptor;
import hudson.remoting.Callable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
    
    public static final int WORKSPACE_TIMEOUT = 1000*60*5;


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
                DiskUsageUtil.calculateDiskUsageForProject(project);
                //well, this is not absolutely thread-safe, but in the worst case we get invalid result for one build
                //(which will be rewritten next time)
                if (!project.isBuilding()) {

                    List<AbstractBuild> builds = project.getBuilds();
                    Iterator<AbstractBuild> buildIterator = builds.iterator();
                    try {

                        while (buildIterator.hasNext()) {
                            DiskUsageUtil.calculateDiskUsageForBuild(buildIterator.next());
                        }

                        //Assign workspace size to the last build
                        DiskUsageUtil.calculateWorkspaceDiskUsage(project);

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
            if (item instanceof MatrixProject || item instanceof MavenModuleSet) { 
                items.add(item);
            } else if (item instanceof ItemGroup) {
                addAllItems((ItemGroup) item, items);
            } else {
                items.add(item);
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
            return getFileSize(f, exceeded);
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
    }
}
