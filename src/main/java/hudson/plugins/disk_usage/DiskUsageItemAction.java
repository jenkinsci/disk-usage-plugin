/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.model.Item;
import java.util.Date;
import java.util.Map;

/**
 *
 * @author Lucie Votypkova
 */
public interface DiskUsageItemAction {
    
    public Map<String,Long> getBuildsDiskUsage(Date older, Date younger, boolean cached);
    
    public Long getAllDiskUsageWorkspace(boolean cached);
    
    public Long getAllCustomOrNonSlaveWorkspaces(boolean cached);
    
    public Long getAllDiskUsage(boolean cached);
    
    public void actualizeCachedData();
    
    public void actualizeCachedBuildsData();
    
    public void actualizeCachedJobWithoutBuildsData();
    
    public void actualizeCachedWorkspaceData();
    
    public void actualizeCachedNotCustomWorkspaceData();
    
    public void actualizeAllCachedDate();
    
    public Long getAllDiskUsageWithoutBuilds(boolean cached);

}
