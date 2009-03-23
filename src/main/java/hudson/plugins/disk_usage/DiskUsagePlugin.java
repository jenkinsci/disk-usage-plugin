package hudson.plugins.disk_usage;

import hudson.Plugin;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Jobs;
import hudson.model.ManagementLink;
import hudson.triggers.Trigger;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
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

    public void start() throws Exception {

        ManagementLink.LIST.add(new ManagementLink() {

            public final String[] COLUMNS = new String[]{"Project name", "Builds", "Workspace"};

            public String getIconFileName() {
                return "/plugin/disk-usage/icons/diskusage48.png";
            }

            public String getDisplayName() {
                return "Disk usage";
            }

            public String getUrlName() {
                return "plugin/disk-usage/";
            }

            public String getDescription() {
                return "Displays per-project disk usage";
            }
        });
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
        
        List<AbstractProject> projectList = Util.createSubList(Hudson.getInstance().getItems(), AbstractProject.class);
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

    public static DiskUsage getDiskUsageSum() {
        return diskUsageSum;
    }
    
    public void doRecordDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException {
        duThread.doRun();
        
        res.forwardToPreviousPage(req);
    }
}
