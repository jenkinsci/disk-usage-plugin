package hudson.plugins.disk_usage;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor.FormException;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
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

    
