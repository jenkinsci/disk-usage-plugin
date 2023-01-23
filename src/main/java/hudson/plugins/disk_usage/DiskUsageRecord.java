/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageRecord {
    private Long date;
    private Long diskUsageBuilds = 0l;
    private Long diskUsageJobsWithoutBuilds = 0l;
    private Long diskUsageWorkspaces = 0l;
    private Long allSpace = 0l;
    private Long diskUsageNonSlaveWorkspaces = 0l;

    public DiskUsageRecord(Long diskUsageBuilds, Long diskUsageWorkspaces, Long diskUsageJobsWithoutBuilds, Long allSpace, Long diskUsageNonSlaveWorkspaces){
            this.diskUsageBuilds = diskUsageBuilds;
            this.diskUsageJobsWithoutBuilds = diskUsageJobsWithoutBuilds;
            this.diskUsageWorkspaces = diskUsageWorkspaces;
            this.allSpace = allSpace;
            this.diskUsageNonSlaveWorkspaces = diskUsageNonSlaveWorkspaces;
            date = System.currentTimeMillis();
    }

    public Long getBuildsDiskUsage(){
        if(diskUsageBuilds==null)
            return 0l;
        return diskUsageBuilds;
    }
    
    public Long getNonSlaveWorkspacesUsage(){
        return diskUsageNonSlaveWorkspaces;
    }
    
    public Long getSlaveWorkspacesUsage(){
        return diskUsageWorkspaces - diskUsageNonSlaveWorkspaces;
    }

    public Long getJobsDiskUsage(){
        if(diskUsageJobsWithoutBuilds==null)
            return getBuildsDiskUsage();
        return diskUsageJobsWithoutBuilds + getBuildsDiskUsage();
    }

    public Long getAllSpace(){
        if(allSpace==null)
            return 0l;
        return allSpace;
    }

    public Long getWorkspacesDiskUsage(){
        if(diskUsageWorkspaces==null)
            return 0l;
        return diskUsageWorkspaces;
    }

    Date getDate(){
        final SimpleDateFormat sdf = new SimpleDateFormat("d/M");
           return new Date(date){
                    private static final long serialVersionUID = 1L;
                    @Override
                    public String toString(){
                            return sdf.format(this);
                    }
            };
    }
}
    
