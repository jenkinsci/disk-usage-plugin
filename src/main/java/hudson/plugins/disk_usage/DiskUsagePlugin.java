package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.Plugin;
import hudson.Util;
import hudson.model.*;
import hudson.util.ChartUtil;
import hudson.util.DataSetBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Entry point of the the plugin.
 *
 * @author dvrzalik
 * @plugin
 */
public class DiskUsagePlugin extends Plugin {
    

    private transient final DiskUsageThread duThread = new DiskUsageThread();
    
    private static DiskUsage diskUsageSum;

    @Extension
    public static class DiskUsageManagementLink extends ManagementLink implements RootAction {

        public final String[] COLUMNS = new String[]{"Project name", "Builds", "Workspace"};

        public String getIconFileName() {
            return "/plugin/disk-usage/icons/diskusage48.png";
        }

        public String getDisplayName() {
            return Messages.DisplayName();
        }

        public String getUrlName() {
            return Hudson.getInstance().getRootUrl() + "plugin/disk-usage/";
        }

        @Override public String getDescription() {
            return Messages.Description();
        }
    }
    
    /**
     * @return DiskUsage for given project (shortcut for the view). Never null.
     */
    public static DiskUsage getDiskUsage(Job project) {
        ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
        if (action != null) {
            return action.getDiskUsage();
        }
        
        return new DiskUsage(0, 0);
    }
    
    //Another shortcut
    public static String getProjectUrl(Job project) {
        return Util.encode(project.getAbsoluteUrl());
    }
    
    /**
     * @return Project list sorted by occupied disk space
     */
    public static List getProjectList() {
        Comparator<AbstractProject> comparator = new Comparator<AbstractProject>() {

            public int compare(AbstractProject o1, AbstractProject o2) {
                
                DiskUsage du1 = getDiskUsage(o1);
                DiskUsage du2 = getDiskUsage(o2);
                
                long result = du2.wsUsage + du2.buildUsage - du1.wsUsage - du1.buildUsage;
                
                if(result > 0) return 1;
                if(result < 0) return -1;
                return 0;
            }
        };

        List<AbstractProject> projectList = addAllProjects(Hudson.getInstance(), new ArrayList<AbstractProject>());
        Collections.sort(projectList, comparator);
        
        //calculate sum
        DiskUsage sum = new DiskUsage(0, 0);
        for(AbstractProject project: projectList) {
            DiskUsage du = getDiskUsage(project);
            sum.buildUsage += du.buildUsage;
            sum.wsUsage += du.wsUsage;
        }
        
        diskUsageSum = sum;
        
        return projectList;
    }

    /**
     * Recursively add Projects form itemGroup
     */
    public static List<AbstractProject> addAllProjects(ItemGroup<? extends Item> itemGroup, List<AbstractProject> items) {
        for (Item item : itemGroup.getItems()) {
            if (item instanceof AbstractProject) {
                items.add((AbstractProject) item);
            } else if (item instanceof ItemGroup) {
                addAllProjects((ItemGroup) item, items);
            }
        }
        return items;
    }

    public static DiskUsage getDiskUsageSum() {
        return diskUsageSum;
    }
    
    public void doRecordDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException {
        duThread.doRun();
        
        res.forwardToPreviousPage(req);
    }
    
    /**
     * Generates a graph with disk usage trend
     *
     */
    public void doGraph(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (ChartUtil.awtProblemCause != null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
            return;
        }

        //TODO if(nothing_changed) return;

        long maxValue = 0;
        //First iteration just to get scale of the y-axis
        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : DiskUsageOvearallGraphGenerator.DESCRIPTOR.history ){
            maxValue = Math.max(maxValue, Math.max(usage.getWsUsage(), usage.getBuildUsage()));
        }

        int floor = (int) DiskUsage.getScale(maxValue);
        String unit = DiskUsage.getUnitString(floor);
        double base = Math.pow(1024, floor);

        DataSetBuilder<String, Date> dsb = new DataSetBuilder<String, Date>();

        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : DiskUsageOvearallGraphGenerator.DESCRIPTOR.history ) {
			Date label = usage.getDate();
            dsb.add(((Long) usage.getWsUsage()) / base, "workspace", label);
            dsb.add(((Long) usage.getBuildUsage()) / base, "build", label);
        }

        ChartUtil.generateGraph(req, rsp, ProjectDiskUsageAction.createChart(req, dsb.build(), unit), 350, 150);
    }

    public int getCountInterval(){
    	return duThread.COUNT_INTERVAL_MINUTES;
    }
}
