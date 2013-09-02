/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import hudson.model.TopLevelItem;
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

	Long diskUsageBuilds = 0l;
        Long diskUsageJobsWithoutBuilds = 0l;
        Long diskUsageWorkspaces =0l;
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        for(TopLevelItem item: Jenkins.getInstance().getItems()) {
            AbstractProject project = (AbstractProject) item;
            ProjectDiskUsageAction action = (ProjectDiskUsageAction) project.getAction(ProjectDiskUsageAction.class);
            diskUsageBuilds += action.getBuildsDiskUsage();
            diskUsageWorkspaces += action.getAllDiskUsageWorkspace();
            diskUsageJobsWithoutBuilds += action.getAllDiskUsageWithoutBuilds();
        }

		plugin.getHistory().add(new DiskUsageRecord(diskUsageBuilds, diskUsageWorkspaces, diskUsageJobsWithoutBuilds));
		plugin.save();

	}

}
