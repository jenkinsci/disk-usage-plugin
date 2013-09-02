/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author lucinka
 */
public class DiskUsageRecord {
		Long date;
                protected Long diskUsageBuilds = 0l;
                protected Long diskUsageJobsWithoutBuilds = 0l;
                protected Long diskUsageWorkspaces = 0l;

		public DiskUsageRecord(Long diskUsageBuilds, Long diskUsageWorkspaces, Long diskUsageJobsWithoutBuilds){
			this.diskUsageBuilds = diskUsageBuilds;
                        this.diskUsageJobsWithoutBuilds = diskUsageJobsWithoutBuilds;
                        this.diskUsageWorkspaces = diskUsageWorkspaces;
			date = System.currentTimeMillis();
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
    
