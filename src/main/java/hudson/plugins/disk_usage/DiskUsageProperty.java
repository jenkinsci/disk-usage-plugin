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
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

//(basically nothing to see here)
/**
 * This Property sets DiskUsage action. 
 * 
 * @author dvrzalik
 */
public class DiskUsageProperty extends JobProperty<Job<?, ?>> {
    
     @Override
    public Action getJobAction(Job<?, ?> job) {
        return new ProjectDiskUsageAction((AbstractProject) job);//??
    }
     
    public JobPropertyDescriptor getDescriptor() {
        return DESCRIPTOR;
    }
    
    public static final JobPropertyDescriptor DESCRIPTOR = new DiskUsageDescriptor();

    public static final class DiskUsageDescriptor extends JobPropertyDescriptor {

        
        public DiskUsageDescriptor() {
            super(DiskUsageProperty.class);
        }
        
        @Override
        public String getDisplayName() {
            return "Disk usage";
        }
        

        @Override
        public DiskUsageProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
             return new DiskUsageProperty();
        }

        @Override
        public boolean configure(StaplerRequest req) throws FormException {
            return true;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return false; // this shouldn't show on the configuration page
        }        
    }         
}

    
