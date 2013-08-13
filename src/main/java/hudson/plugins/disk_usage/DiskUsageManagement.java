/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.model.ManagementLink;
import hudson.model.RootAction;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import java.io.IOException;
import java.util.Date;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author lucinka
 */
public class DiskUsageManagement extends ManagementLink implements RootAction{

   public final String[] COLUMNS = new String[]{"Project name", "Builds", "Workspace", "JobDirectory (without builds)"};

        public String getIconFileName() {
            return "/plugin/disk-usage/icons/diskusage48.png";
        }

        public String getDisplayName() {
            return Messages.DisplayName();
        }

        public String getUrlName() {
            return "plugin/disk-usage/";
        }

        @Override public String getDescription() {
            return Messages.Description();
        }
        
        /**
     * Generates a graph with disk usage trend
     *
     */
     public Graph getOverallGraph(){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        long maxValue = 0;
        //First iteration just to get scale of the y-axis
        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : plugin.getHistory() ){
            maxValue = Math.max(maxValue, Math.max(usage.diskUsageJobsWithoutBuilds, usage.diskUsageWorkspaces));
        }

        int floor = (int) DiskUsageUtil.getScale(maxValue);
        String unit = DiskUsageUtil.getUnitString(floor);
        double base = Math.pow(1024, floor);

        DataSetBuilder<String, Date> dsb = new DataSetBuilder<String, Date>();

        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : plugin.getHistory() ) {
			Date label = usage.getDate();
            dsb.add(((Long) usage.diskUsageWorkspaces) / base, "workspace", label);
            dsb.add(((Long) usage.diskUsageBuilds) / base, "build", label);
            dsb.add(((Long) usage.diskUsageJobsWithoutBuilds) / base, "job directory (without builds)", label);
            dsb.add(((Long) usage.diskUsageJenkinsHome) / base, "henkins home", label);
        }

		return new DiskUsageGraph(dsb.build(), unit);
	}
     
     public void doRecordDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
         plugin.getBuildsDiskuUsateThread().doRun();
        plugin.getJobsDiskuUsateThread().doRun();
        res.forwardToPreviousPage(req);
    }
    
}
