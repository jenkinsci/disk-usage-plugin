package hudson.plugins.disk_usage;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.*;
import hudson.plugins.disk_usage.DiskUsageThread.DiskUsageCallable;
import hudson.Extension;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

//(basically nothing to see here)
/**
 * This Property sets DiskUsage action. 
 * 
 * @author dvrzalik
 */
public class DiskUsageProperty extends JobProperty<Job<?, ?>> {

    @Override
    public Collection<? extends Action> getJobActions(Job<?, ?> job) {
        return Collections.emptyList();
    }

    @Extension
    public static final class DiskUsageDescriptor extends JobPropertyDescriptor {
        
        private Long diskUsageWithoutBuilds = 0l;
        private Map<String,Long> slaveWorkspacesUsage = new TreeMap<String,Long>();

        public DiskUsageDescriptor() {
            load();
        }
        
        public void setDiskUsageWithoutBuilds(Long diskUsageWithoutBuilds){
            this.diskUsageWithoutBuilds = diskUsageWithoutBuilds;
        }
        
        public void putSlaveWorkspace(Node node, Long size){
            slaveWorkspacesUsage.put(node.getNodeName(), size);
        }
        
        public Map<String,Long> getSlaveWorkspaceUsage(){
            return slaveWorkspacesUsage;
        }
        
        public Long getDiskUsageWithoutBuilds(){
            return diskUsageWithoutBuilds;
        }

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }


        @Override
        public DiskUsageProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
             return new DiskUsageProperty();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            return true;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }
    }
    
    public static final Logger LOGGER = Logger.getLogger(DiskUsageProperty.class.getName());
}

    
