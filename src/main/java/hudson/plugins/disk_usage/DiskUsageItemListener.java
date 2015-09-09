package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
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
    }
    
    @Override
     public void onRenamed(Item item, String oldName, String newName) {
         if(item instanceof AbstractProject)
            DiskUsageProjectActionFactory.DESCRIPTOR.onRenameJob(oldName, newName);
    } 
    
    @Override
    public void onCreated(Item item){
        DiskUsageUtil.addProperty(item);

    }   
    
    @Override
    public void onCopied(Item src, Item item){
        DiskUsageUtil.addProperty(item);
    }
    
    @Override
    public void onLoaded(){
        for(Item item : Jenkins.getInstance().getItems()){
            DiskUsageUtil.addProperty(item);
        }
    }
}
