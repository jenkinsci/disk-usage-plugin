package hudson.plugins.disk_usage;


import hudson.model.*;
import hudson.Extension;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

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
    
      private Long diskUsageWithoutBuilds = 0l;
     private Map<String,Long> slaveWorkspacesUsage = new TreeMap<String,Long>();
     
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
        
        public Long getAllDiskUsageWithoutBuilds(){
           Long diskUsage = diskUsageWithoutBuilds;
           if(owner instanceof ItemGroup){
                     ItemGroup group = (ItemGroup) owner;
                         diskUsage += getDiskUsageWithoutBuildsAllSubItems(group);
           }
           return diskUsage;
        }
        
        private Long getDiskUsageWithoutBuildsAllSubItems(ItemGroup group){
        Long diskUsage = 0l;
        for(Object item: group.getItems()){
            if(item instanceof ItemGroup){
               ItemGroup subGroup = (ItemGroup) item;
               diskUsage += getDiskUsageWithoutBuildsAllSubItems(subGroup);
            }
            if(item instanceof AbstractProject){
                AbstractProject p = (AbstractProject) item;
                DiskUsageProperty property = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                if(property!=null){
                    diskUsage += property.getDiskUsageWithoutBuilds();
                }
            }
        }
        return diskUsage;
    }

    @Extension
    public static final class DiskUsageDescriptor extends JobPropertyDescriptor {

        public DiskUsageDescriptor() {
            load();
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

    
