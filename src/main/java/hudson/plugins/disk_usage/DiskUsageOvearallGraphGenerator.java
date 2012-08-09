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
public class DiskUsageOvearallGraphGenerator extends PeriodicWork implements Describable<DiskUsageOvearallGraphGenerator> {

	@Override
	public long getRecurrencePeriod() {
		return PeriodicWork.MIN;
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

		DESCRIPTOR.history.add(new DiskUsageRecord(sum));
		DESCRIPTOR.save();

	}

	public Descriptor<DiskUsageOvearallGraphGenerator> getDescriptor() {
		return DESCRIPTOR;
	}

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<DiskUsageOvearallGraphGenerator> {
		final int SIZE = 10;
		List<DiskUsageRecord> history = new LinkedList<DiskUsageRecord>(){
				private static final long serialVersionUID = 1L;

				@Override
				public boolean add(DiskUsageRecord e) {
					boolean ret = super.add(e);
					if(ret && this.size() > SIZE){
						this.removeFirst();
					}
					return ret;
				}
			};

		public DescriptorImpl() {
           load();
        }

		@Override
		public String getDisplayName() {
			return Messages.DisplayName();
		}

	}

	public static class DiskUsageRecord extends DiskUsage{
		private static SimpleDateFormat sdf = new SimpleDateFormat("d/M H:m");
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
