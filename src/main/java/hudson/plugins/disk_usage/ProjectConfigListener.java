/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class ProjectConfigListener extends ItemListener{
    
    @Override
    public void onUpdated(Item item){
        if(item instanceof AbstractProject){
           AbstractProject project = (AbstractProject) item;
           DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
           if(property==null){
                try {
                    //project should always have instance of DiskUsageProperty
                    project.addProperty(new DiskUsageProperty());
                } catch (IOException ex) {
                    Logger.getLogger(ProjectConfigListener.class.getName()).log(Level.SEVERE, null, ex);
                }
           }
        }
    }
}
