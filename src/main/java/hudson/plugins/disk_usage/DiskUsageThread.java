package hudson.plugins.disk_usage;

import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import hudson.model.Project;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.Axis;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.remoting.Callable;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.LogRotator;
import hudson.tasks.Publisher;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A Thread responsible for gathering disk usage information
 * 
 * @author dvrzalik
 */
public class DiskUsageThread extends PeriodicWork {

    public DiskUsageThread() {
        super("Disk usage");
    }

    @Override
    protected void execute() {
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

                if (!project.isBuilding()) {

                    List<AbstractBuild> builds = project.getBuilds();
                    Iterator<AbstractBuild> buildIterator = builds.iterator();
                    try {
                        //Assign workspace size to the last build
                        if (buildIterator.hasNext()) {
                            calculateDiskUsageForBuild(buildIterator.next(), true);
                        }

                        while (buildIterator.hasNext()) {
                            calculateDiskUsageForBuild(buildIterator.next(), false);
                        }

                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Error when recording disk usage for " + project.getName(), ex);
                    }
                }
            }
        }
    }

    private static void calculateDiskUsageForBuild(AbstractBuild build, boolean countWorkspace)
            throws IOException, InterruptedException {

        if (build.isBuilding()) {
            return;
        }

        BuildDiskUsageAction bdua = build.getAction(BuildDiskUsageAction.class);
        if (bdua == null) {
            long wsUsage = 0;
            if (countWorkspace) {
                AbstractProject parent = build.getProject();
                AbstractBuild lastBuild = (AbstractBuild) parent.getLastBuild();
                if (lastBuild != null) {
                    FilePath workspace = parent.getWorkspace();
                    wsUsage = workspace.act(new DiskUsageCallable(workspace));
                }
            }

            long buildSize = DiskUsageCallable.getFileSize(build.getRootDir());
            BuildDiskUsageAction action = new BuildDiskUsageAction(build, wsUsage, buildSize);
            build.addAction(action);
            build.save();
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
