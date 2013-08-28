/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.WorkspaceListener;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageWorkspaceListener extends WorkspaceListener{
    
    @Override
    public void afterDelete(AbstractProject project){
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        property.checkWorkspaces();
    }
    
}
