/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

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

		DiskUsage sum = new DiskUsage(0, 0);
        for(AbstractProject project: projectList) {
            DiskUsage du = DiskUsagePlugin.getDiskUsage(project);
            sum.buildUsage += du.buildUsage;
            sum.wsUsage += du.wsUsage;
        }

		DiskUsageProjectActionFactory.DESCRIPTOR.history.add(new DiskUsageRecord(sum));
		DiskUsageProjectActionFactory.DESCRIPTOR.save();

	}

	public static class DiskUsageRecord extends DiskUsage{
		private static SimpleDateFormat sdf = new SimpleDateFormat("d/M");
		Date date;

		public DiskUsageRecord(DiskUsage du){
			super(du.buildUsage, du.wsUsage);
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
