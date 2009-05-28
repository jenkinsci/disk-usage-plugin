package hudson.plugins.disk_usage;

import hudson.model.PeriodicWork;
import hudson.FilePath;
import hudson.Util;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.remoting.Callable;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

/**
 * A Thread responsible for gathering disk usage information
 * 
 * @author dvrzalik
 */
@Extension
public class DiskUsageThread extends AsyncPeriodicWork {
    //trigger disk usage thread each 60 minutes
    public static final int COUNT_INTERVAL_MINUTES = 60;


    public DiskUsageThread() {
        super("Project disk usage");
    }

    public long getRecurrencePeriod() {
        return 1000*60*COUNT_INTERVAL_MINUTES;
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        logger.log(Level.INFO, "Starting disk usage thread");

        List items = Hudson.getInstance().getItems();

        //Include nested projects as well
        //TODO fix MatrixProject and use getAllJobs()
        for (TopLevelItem item : Hudson.getInstance().getItems()) {
            if(item instanceof ItemGroup) {
                items.addAll(((ItemGroup)item).getItems());
            }
        }

        for (Object item : items) {
            if (item instanceof AbstractProject) {
                AbstractProject project = (AbstractProject) item;
                if (project.getAction(ProjectDiskUsageAction.class) == null) {
                    try {
                        project.addProperty(new DiskUsageProperty());
                        project.save();
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Error when adding disk usage property for " + project.getName(), ex);
                        break;
                    }
                }

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

    private static void calculateDiskUsageForBuild(AbstractBuild build)
            throws IOException {

        //Build disk usage has to be always recalculated to be kept up-to-date 
        //- artifacts might be kept only for the last build and users sometimes delete files manually as well.
        long buildSize = DiskUsageCallable.getFileSize(build.getRootDir());
        BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
        if (action == null) {
            action = new BuildDiskUsageAction(build, 0, buildSize);
            build.addAction(action);
        } else {
            action.diskUsage.buildUsage = buildSize;
        }

        build.save();
    }
    
    private static void calculateWorkspaceDiskUsage(AbstractProject project) throws IOException, InterruptedException {
        AbstractBuild lastBuild = (AbstractBuild) project.getLastBuild();
        if (lastBuild != null) {
            BuildDiskUsageAction bdua = lastBuild.getAction(BuildDiskUsageAction.class);
            if (bdua == null) {
                bdua = new BuildDiskUsageAction(lastBuild, 0, 0);
                lastBuild.addAction(bdua);
            }
            FilePath workspace = project.getWorkspace();
            //slave might be offline...
            if ((workspace != null) && (bdua.diskUsage.wsUsage <= 0)) {
                bdua.diskUsage.wsUsage = workspace.act(new DiskUsageCallable(workspace));
                lastBuild.save();
            }
        }
    }

    /**
     * A {@link Callable} which computes disk usage of remote file object
     */
    public static class DiskUsageCallable implements Callable<Long, IOException> {

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
                for (File child : f.listFiles()) {
                    size += getFileSize(child);
                }
            }
            
            return size + f.length();
        }
    }
}
