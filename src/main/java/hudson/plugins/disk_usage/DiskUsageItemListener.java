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
    }
    
    @Override
     public void onRenamed(Item item, String oldName, String newName) {
         if(item instanceof AbstractProject)
            DiskUsageProjectActionFactory.DESCRIPTOR.onRenameJob(oldName, newName);
    } 
    
    public void onCreated(Item item){
        addProperty(item);
    }
    
    public void addProperty(Item item){
            if(item instanceof AbstractProject){
                AbstractProject project = (AbstractProject) item;
                DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
                if(property==null){
                    try {
                        project.addProperty(new DiskUsageProperty());

                    } catch (IOException ex) {
                        Logger.getLogger(DiskUsageItemListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                else{
                    //try if there is load data to load 
                    property.getDiskUsage().loadOldData();
                }
            }
            if(item instanceof ItemGroup){
                for(AbstractProject project : DiskUsageUtil.getAllProjects((ItemGroup)item)){
                    DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
                    if(property==null){
                        try {
                            project.addProperty(new DiskUsageProperty());
                        } catch (IOException ex) {
                            Logger.getLogger(DiskUsageItemListener.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    else{
                    //try if there is load data to load 
                        property.getDiskUsage().loadOldData();
                    }
                }
            }
    }
    
    public void onLoaded(){
        for(Item item : Jenkins.getInstance().getItems()){
            addProperty(item);
        }
    }
}
