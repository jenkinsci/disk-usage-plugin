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
import jenkins.model.Jenkins;
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
    

    private transient final BuildDiskUsageCalculationThread builsdDuThread = new BuildDiskUsageCalculationThread();
    
    private transient final JobWithoutBuildsDiskUsageCalculation jobsDuThread = new JobWithoutBuildsDiskUsageCalculation();
    
    private String countIntervalBuilds; 
    
    private String countIntervalJobs;
    
    private String countIntervalWorkspace;
    
    private String countIntervalJenkinsHome;
    
    private  int workspaceTimeOut = 1000*60*5;
    
    protected static Long diskUsageBuilds = 0l;
    protected static Long diskUsageJenkinsHome =0l;
    protected static Long diskUsageJobsWithoutBuilds = 0l;
    protected static Long diskUsageWorkspaces = 0l;
    
    private boolean showGraph = true;
    private int historyLength = 183;
    private List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> history = new LinkedList<DiskUsageOvearallGraphGenerator.DiskUsageRecord>(){
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
    
    public BuildDiskUsageCalculationThread getBuildsDiskuUsateThread(){
        return builsdDuThread;
    }
    
    public JobWithoutBuildsDiskUsageCalculation getJobsDiskuUsateThread(){
        return jobsDuThread;
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

        List<AbstractProject> projectList = new ArrayList<AbstractProject>();
        projectList.addAll(DiskUsageUtil.getAllProjects(Jenkins.getInstance()));
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
    
     public boolean Configure(StaplerRequest req, StaplerResponse res) throws ServletException, IOException{
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
        
        public List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> getHistory(){
            return history;
        }

    public String getCountIntervalForBuilds(){
    	return countIntervalBuilds;
    }
    
    public String getCountIntervalForJobs(){
    	return countIntervalJobs;
    }
    
    public String getCountIntervalForWorkspaces(){
    	return countIntervalWorkspace;
    }
    
    public String getCountIntervalForJenkinsHome(){
    	return countIntervalJenkinsHome;
    }
    
    
}
