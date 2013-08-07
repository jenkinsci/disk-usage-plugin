/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jenkins.model.Jenkins;

/**
 *
 * @author jbrazdil
 */
@Extension
public class DiskUsageOvearallGraphGenerator extends PeriodicWork {

	@Override
	public long getRecurrencePeriod() {
		return PeriodicWork.DAY;
	}

	@Override
	protected void doRun() throws Exception {
        List<AbstractProject> projectList = DiskUsagePlugin.addAllProjects(Hudson.getInstance(), new ArrayList<AbstractProject>());

	Long diskUsageBuilds = 0l;
        Long diskUsageJobsWithoutBuilds = 0l;
        Long diskUsageWorkspaces =0l;
        Long diskUsageJenkinsHome = 0l;
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        for(AbstractProject project: projectList) {
            ProjectDiskUsageAction action = (ProjectDiskUsageAction) project.getActions(ProjectDiskUsageAction.class);
            diskUsageBuilds += action.getBuildsDiskUsage();
            diskUsageWorkspaces += action.getDiskUsageWorkspace();
            diskUsageJobsWithoutBuilds += action.getDiskUsageWithoutBuilds();
        }

		plugin.history.add(new DiskUsageRecord(diskUsageBuilds, diskUsageWorkspaces, diskUsageJobsWithoutBuilds, diskUsageJenkinsHome));
		plugin.save();

	}

	public static class DiskUsageRecord {
		private static SimpleDateFormat sdf = new SimpleDateFormat("d/M");
		Date date;
                protected Long diskUsageBuilds = 0l;
                protected Long diskUsageJenkinsHome =0l;
                protected Long diskUsageJobsWithoutBuilds = 0l;
                protected Long diskUsageWorkspaces = 0l;

		public DiskUsageRecord(Long diskUsageBuilds, Long diskUsageWorkspaces, Long diskUsageJobsWithoutBuilds, Long diskUsageJenkinsHome){
			this.diskUsageBuilds = diskUsageBuilds;
                        this.diskUsageJenkinsHome = diskUsageJenkinsHome;
                        this.diskUsageJobsWithoutBuilds = diskUsageJobsWithoutBuilds;
                        this.diskUsageWorkspaces = diskUsageWorkspaces;
			date = new Date(){
				private static final long serialVersionUID = 1L;
				@Override
				public String toString(){
					return sdf.format(this);
				}
			};
		}

		Date getDate(){
			return date;
		}
	}

}
