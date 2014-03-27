package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageItemListener extends ItemListener{
    
    @Override
    public void onDeleted(Item item) {
        if(item instanceof AbstractProject)
            DiskUsageProjectActionFactory.DESCRIPTOR.onDeleteJob((AbstractProject) item); 
    }
    
    @Override
     public void onRenamed(Item item, String oldName, String newName) {
         if(item instanceof AbstractProject)
            DiskUsageProjectActionFactory.DESCRIPTOR.onRenameJob(oldName, newName);
    }
    
}
