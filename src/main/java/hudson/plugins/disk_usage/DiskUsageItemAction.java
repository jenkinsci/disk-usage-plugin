/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import java.util.Date;
import java.util.Map;

/**
 *
 * @author Lucie Votypkova
 */
public interface DiskUsageItemAction {
    
    public Long getDiskUsageWithoutBuilds();
    
    public Map<String,Long> getBuildsDiskUsage(Date older, Date younger);
    
    public Long getAllDiskUsageWorkspace();
    
    public Long getAllCustomOrNonSlaveWorkspaces();
    
    public Long getAllDiskUsage();
    
}
