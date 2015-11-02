package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.listeners.ItemListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

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
        if(item instanceof ItemGroup)
           Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).removeDiskUsageItemGroup((ItemGroup)item); 
    }
    
    @Override
     public void onRenamed(Item item, String oldName, String newName) {
         if(item instanceof AbstractProject)
            DiskUsageProjectActionFactory.DESCRIPTOR.onRenameJob(oldName, newName);
    } 
    
    @Override
    public void onCreated(Item item){
        DiskUsageUtil.addProperty(item);
        if(item instanceof ItemGroup)
            try {
            Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).putDiskUsageItemGroup((ItemGroup)item);
        } catch (IOException ex) {
            Logger.getLogger(DiskUsageItemListener.class.getName()).log(Level.SEVERE, null, ex);
        }

    }   
    
    @Override
    public void onCopied(Item src, Item item){
        DiskUsageUtil.addProperty(item);
        if(item instanceof ItemGroup)
            try {
            Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).putDiskUsageItemGroup((ItemGroup)item);
        } catch (IOException ex) {
            Logger.getLogger(DiskUsageItemListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public void onLoaded(){
        Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).loadDiskUsageItemGroups();
    }
}
