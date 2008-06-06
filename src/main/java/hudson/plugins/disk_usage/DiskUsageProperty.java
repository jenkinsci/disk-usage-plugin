package hudson.plugins.disk_usage;

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
import hudson.remoting.Callable;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.LogRotator;
import hudson.tasks.Publisher;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A {@link Publisher} responsible for counting disk usage for a project.
 * 
 * @author dvrzalik
 */
public class DiskUsageProperty extends JobProperty<Job<?, ?>> implements MatrixAggregatable {
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final AbstractProject project = build.getProject();

        long time = System.currentTimeMillis();
        listener.getLogger().print("\n\nCalculating disk usage for workspace directory... ");
        FilePath workspace = project.getWorkspace();
        long wsSize = launcher.getChannel().call(new DiskUsageCallable(workspace));
        listener.getLogger().println("done.\n");

        listener.getLogger().print("Calculating disk usage for builds ... ");
        calculateDiskUsageForBuild(build);
        
        listener.getLogger().println("done.\n");
        listener.getLogger().println((System.currentTimeMillis() - time) / 1000);

        //Set workspace size for the last run
        build.getAction(BuildDiskUsageAction.class).wsUsage = wsSize;
        build.save();

        return true;
    }

     @Override
    public Action getJobAction(Job<?, ?> job) {
        return new ProjectDiskUsageAction((AbstractProject) job);//??
    }
     
    public JobPropertyDescriptor getDescriptor() {
        return DESCRIPTOR;
    }
    
    public static final JobPropertyDescriptor DESCRIPTOR = new DiskUsageDescriptor();

    public static final class DiskUsageDescriptor extends JobPropertyDescriptor {

        //Force disk usage count
        private boolean force = false;
        
        public DiskUsageDescriptor() {
            super(DiskUsageProperty.class);
        }
        
        @Override
        public String getDisplayName() {
            return "Disk usage";
        }
        

        @Override
        public DiskUsageProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if(force || req.getParameter("disk_usage.record") != null) {
                return new DiskUsageProperty();
            }
            return null;
        }

        @Override
        public boolean configure(StaplerRequest req) throws FormException {
            force = req.getParameter("disk_usage.force") != null;
            save();
            return true;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        public boolean isForce() {
            return force;
        }
        
        
    }         
    
    private static long calculateDiskUsageForBuild(Run build) 
            throws InterruptedException, IOException {   
        Job parent = build.getParent();
        
        //FIXME: include parent build size (log, etc.)
        if(parent instanceof ItemGroup) {
            long buildSize = DiskUsageCallable.getFileSize(build.getRootDir());
            for (Object child : ((ItemGroup) parent).getItems()) {

                if (child instanceof Job) {
                    Run childBuild = ((Job) child).getNearestBuild(build.getNumber());
                    Run nextBuild = build.getNextBuild();
                    Integer nextBuildNumber = (nextBuild != null) ? nextBuild.getNumber() : Integer.MAX_VALUE;
                    while((childBuild != null) && (childBuild.getNumber() < nextBuildNumber)) {
                        buildSize += _doCalculateDiskUsage(childBuild);
                        childBuild = childBuild.getNextBuild();
                    }
                }
            }
            
            BuildDiskUsageAction bdua = new BuildDiskUsageAction(0, buildSize,0);
            //set the action first, so that counting all builds usage works
            build.addAction(bdua);
            bdua.allBuildsUsage = allBuildUsages(build);
            build.save();
            
            return buildSize;
        } else {
            return _doCalculateDiskUsage(build);
        }
    }
    
    private static long _doCalculateDiskUsage(Run build) 
            throws InterruptedException, IOException {
        long buildSize = 0;
        BuildDiskUsageAction bdua = build.getAction(BuildDiskUsageAction.class);
        if(bdua != null) {
            buildSize = bdua.getBuildUsage();
        } else {
            buildSize = DiskUsageCallable.getFileSize(build.getRootDir());
            BuildDiskUsageAction action = new BuildDiskUsageAction(0, buildSize, 0);
            build.addAction(action);
            long allBuildsUsage = allBuildUsages(build);
            action.allBuildsUsage = allBuildsUsage;
            build.save();
        }
        
        return buildSize;
    }
    
    /**
     * @return Counts disk usage for the given and previous builds
     */
    public static long allBuildUsages(Run build) throws InterruptedException, IOException {
        long usage = 0;
        
        //Don't count builds to be removed by LogRotator
        int count = 1;
        LogRotator rotator = build.getParent().getLogRotator();
        int numKeep = (rotator != null) ? rotator.getNumToKeep() : -1;
        int daysKeep = (rotator != null) ? rotator.getDaysToKeep() : -1;
        Calendar keepCalendar = null;
        if (daysKeep != -1) {
            keepCalendar = new GregorianCalendar();
            keepCalendar.add(Calendar.DAY_OF_YEAR, -daysKeep);
        }

        Run lastBuild = build.getParent().getLastSuccessfulBuild();

        while (build != null) {
            if (build.isKeepLog() ||
                    ((count <= numKeep) && ((keepCalendar == null) || (build.getTimestamp().after(keepCalendar)))) ||
                    (lastBuild  == build)) {

                BuildDiskUsageAction bdua = build.getAction(BuildDiskUsageAction.class);
                if (bdua != null) {
                    usage += bdua.getBuildUsage();
                } else {
                    usage += _doCalculateDiskUsage(build);
                }
            }

            build = build.getPreviousBuild();
            count++;
        }
        return usage;
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
    
    //----- Hook for Matrix projects
    
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new DiskUsageMatrixAggregator(build, launcher, listener);
    }

    public class DiskUsageMatrixAggregator extends MatrixAggregator {

        public DiskUsageMatrixAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
            super(build, launcher, listener);
        }

        @Override
        public boolean endBuild() throws InterruptedException, IOException {
            return perform(build, launcher, listener);
        }
    }
}

    
