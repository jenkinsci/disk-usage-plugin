package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.Plugin;
import hudson.Util;
import hudson.model.*;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
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
    
    private String countIntervalBuilds; 
    
    private String countIntervalJobs;
    
    private String countIntervalWorkspace;
    
    private String countIntervalJenkinsHome;
    
    private  int workspaceTimeOut = 1000*60*5;
    
    private static Long diskUsageBuilds = 0l;
    private static Long diskUsageJenkinsHome =0l;
    private static Long diskUsageJobsWithoutBuilds = 0l;
    private static Long diskUsageWorkspaces = 0l;
    
    private boolean showGraph = true;
    private int historyLength = 183;
    
		List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> history = new LinkedList<DiskUsageOvearallGraphGenerator.DiskUsageRecord>(){
				private static final long serialVersionUID = 1L;

				@Override
				public boolean add(DiskUsageOvearallGraphGenerator.DiskUsageRecord e) {
					boolean ret = super.add(e);
					if(ret && this.size() > historyLength){
						this.removeRange(0, this.size() - historyLength);
					}
					return ret;
				}
			};
    

    @Extension
    public static class DiskUsageManagementLink extends ManagementLink {

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
    }
    
    
    /**
     * Unfortunately, I cannot figure out any other solution to satisfy JENKINS-12917 and at the same time JENKINS-16420 
     */
    @Extension
    public static class DiskUsageRootLink implements RootAction {

    	public String getIconFileName() {
            return "/plugin/disk-usage/icons/diskusage48.png";
        }

        public String getDisplayName() {
            return Messages.DisplayName();
        }

        public String getUrlName() {
            return "/plugin/disk-usage/";
        }
    }
    
    public int getWorkspaceTimeOut(){
        return workspaceTimeOut;
    }
    
    /**
     * @return DiskUsage for given project (shortcut for the view). Never null.
     */
    public static ProjectDiskUsageAction getDiskUsage(Job project) {
        ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
        return action;
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
                
                ProjectDiskUsageAction dua1 = getDiskUsage(o1);
                ProjectDiskUsageAction dua2 = getDiskUsage(o2);
                
                long result = dua2.getJobRootDirDiskUsage() + dua2.getDiskUsageWorkspace() - dua1.getJobRootDirDiskUsage() - dua1.getDiskUsageWorkspace();
                
                if(result > 0) return 1;
                if(result < 0) return -1;
                return 0;
            }
        };

        List<AbstractProject> projectList = addAllProjects(Hudson.getInstance(), new ArrayList<AbstractProject>());
        Collections.sort(projectList, comparator);
        
        //calculate sum
        diskUsageBuilds = 0l;
        diskUsageJenkinsHome =0l;
        diskUsageJobsWithoutBuilds = 0l;
        diskUsageWorkspaces = 0l;
        for(AbstractProject project: projectList) {
            diskUsageBuilds =+ getDiskUsage(project).getBuildsDiskUsage();
            diskUsageJobsWithoutBuilds =+ getDiskUsage(project).getDiskUsageWithoutBuilds();
            diskUsageWorkspaces =+ getDiskUsage(project).getDiskUsageWorkspace();
        }
        
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
    
    public void doRecordDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException {
        duThread.doRun();
        
        res.forwardToPreviousPage(req);
    }
    
     public boolean doConfigure(StaplerRequest req, StaplerResponse res) throws ServletException, IOException{
            JSONObject form = req.getSubmittedForm();
            countIntervalBuilds = form.getBoolean("countBuildsEnabled")? form.getString("countIntervalBuilds") : null;
            countIntervalJobs = form.getBoolean("countJobsEnabled")? form.getString("countIntervalJobs") : null;
            countIntervalWorkspace = form.getBoolean("countWorkspaceEnabled")? form.getString("countIntervalWorkspace") : null;
            countIntervalJenkinsHome = form.getBoolean("countJenkinsHomeEnabled")? form.getString("countIntervalJenkinsHome") : null;
            workspaceTimeOut = form.getInt("countInterval");
            showGraph = req.getParameter("disk_usage.showGraph") != null;
			String histlen = req.getParameter("disk_usage.historyLength");
			if(histlen != null ){
				try{
					historyLength = Integer.parseInt(histlen);
				}catch(NumberFormatException ex){
					historyLength = 183;
				}
			}else{
				historyLength = 183;
			}
            save();
            return true;
        }
     
      public boolean isShowGraph() {
            //The graph is shown by default
            return showGraph;
        }

        public void setShowGraph(Boolean showGraph) {
            this.showGraph = showGraph;
        }

        public int getHistoryLength() {
            return historyLength;
        }

        public void setHistoryLength(Integer historyLength) {
            this.historyLength = historyLength;
        }
    
    /**
     * Generates a graph with disk usage trend
     *
     */
	public Graph getOverallGraph(){
        long maxValue = 0;
        //First iteration just to get scale of the y-axis
        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : history ){
            maxValue = Math.max(maxValue, Math.max(usage.diskUsageJobsWithoutBuilds, usage.diskUsageWorkspaces));
        }

        int floor = (int) DiskUsageUtil.getScale(maxValue);
        String unit = DiskUsageUtil.getUnitString(floor);
        double base = Math.pow(1024, floor);

        DataSetBuilder<String, Date> dsb = new DataSetBuilder<String, Date>();

        for (DiskUsageOvearallGraphGenerator.DiskUsageRecord usage : history ) {
			Date label = usage.getDate();
            dsb.add(((Long) usage.diskUsageWorkspaces) / base, "workspace", label);
            dsb.add(((Long) usage.diskUsageBuilds) / base, "build", label);
            dsb.add(((Long) usage.diskUsageJobsWithoutBuilds) / base, "job directory (without builds)", label);
            dsb.add(((Long) usage.diskUsageJenkinsHome) / base, "henkins home", label);
        }

		return new DiskUsageGraph(dsb.build(), unit);
	}

    public int getCountInterval(){
    	return duThread.COUNT_INTERVAL_MINUTES;
    }
    
    
}
