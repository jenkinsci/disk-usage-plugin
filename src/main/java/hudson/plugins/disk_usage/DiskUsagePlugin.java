package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.Plugin;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.*;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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
@Extension
public class DiskUsagePlugin extends Plugin {
    

    private transient final BuildDiskUsageCalculationThread builsdDuThread = new BuildDiskUsageCalculationThread();
    
    private transient final JobWithoutBuildsDiskUsageCalculation jobsDuThread = new JobWithoutBuildsDiskUsageCalculation();
    
    private String countIntervalBuilds; 
    
    private String countIntervalJobs;
    
    private String countIntervalWorkspace;
    
    private String countIntervalJenkinsHome;
    
    private boolean checkWorkspaceOnSlave;
    
    private  int workspaceTimeOut = 1000*60*5;
    
    protected static Long diskUsageBuilds = 0l;
    protected static Long diskUsageJenkinsHome =0l;
    protected static Long diskUsageJobsWithoutBuilds = 0l;
    protected static Long diskUsageWorkspaces = 0l;
    
    private boolean showGraph = true;
    private int historyLength = 183;
    private List<DiskUsageRecord> history = new ArrayList<DiskUsageRecord>();
    
    public DiskUsagePlugin(){
        try {
            load();
        } catch (IOException ex) {
            Logger.getLogger(DiskUsagePlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public XmlFile getConfigXml(){
        return new XmlFile(Jenkins.XSTREAM,
                new File(Jenkins.getInstance().getRootDir(),"disk-usage.xml"));
    }
    
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
        
        public List<DiskUsageRecord> getHistory(){
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
    
    public boolean getCheckWorkspaceOnSlave(){
        return checkWorkspaceOnSlave;
    }
    
    public void setCheckWorkspaceOnSlave(boolean check){
        checkWorkspaceOnSlave = check;
    }
    
    public Graph getOverallGraph(){
        long maxValue = 0;
        long maxValueWorkspace = 0l;
        //First iteration just to get scale of the y-axis
        for (DiskUsageRecord usage : history ){
            maxValue = Math.max(maxValue, usage.diskUsageJobsWithoutBuilds + usage.diskUsageBuilds);
            maxValueWorkspace = Math.max(maxValueWorkspace, diskUsageJobsWithoutBuilds + usage.diskUsageBuilds);
        }

        int floor = (int) DiskUsageUtil.getScale(maxValue);
        String unit = DiskUsageUtil.getUnitString(floor);
        double base = Math.pow(1024, floor);
        floor = (int) DiskUsageUtil.getScale(maxValueWorkspace);
        String unitWorkspace = DiskUsageUtil.getUnitString(floor);
        double baseWorkspace = Math.pow(1024, floor);

        DataSetBuilder<String, Date> dsb = new DataSetBuilder<String, Date>();
        DataSetBuilder<String, Date> dsb2 = new DataSetBuilder<String, Date>();
        for (DiskUsageRecord usage : history ) {
            Date label = usage.getDate();
            dsb.add(((Long) usage.diskUsageBuilds) / base, "builds", label);
            dsb.add(((Long) usage.diskUsageJobsWithoutBuilds + usage.diskUsageBuilds) / base, "jobs", label);
            dsb2.add(((Long) usage.diskUsageWorkspaces) / baseWorkspace, "workspaces", label);
        }

            return new DiskUsageGraph(dsb.build(), unit, dsb2.build(), unitWorkspace);
        }  
    
    public void doRecordDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
         plugin.getBuildsDiskuUsateThread().doRun();
        plugin.getJobsDiskuUsateThread().doRun();
        res.forwardToPreviousPage(req);
    }
    
}
