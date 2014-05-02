package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.WorkspaceListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageWorkspaceListener extends WorkspaceListener{
    
    @Override
    public void afterDelete(AbstractProject project){
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property!=null){
            property.checkWorkspaces();
        }
        try {
            project.save();
        } catch (IOException ex) {
            Logger.getLogger(DiskUsageWorkspaceListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
