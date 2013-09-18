package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.Plugin;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.*;
import hudson.security.Permission;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jfree.data.category.DefaultCategoryDataset;
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
    
    private String countIntervalBuilds = "0 */6 * * *"; 
    
    private boolean calculationBuilds = true;
    
    private String countIntervalJobs = "0 */6 * * *";
    
    private boolean calculationJobs = true;
    
    private String countIntervalWorkspace ="0 */6 * * *";
    
    private boolean calculationWorkspace = true;
    
    private boolean checkWorkspaceOnSlave = false;
    
    private String email;
    
    private String jobSize;
    
    private String buildSize;
    
    private String allJobsSize;
    
    private String jobWorkspaceExceedSize;
    
    private  int workspaceTimeOut = 1000*60*5;
    
    private Long diskUsageBuilds = 0l;
    private Long diskUsageJobsWithoutBuilds = 0l;
    private Long diskUsageWorkspaces = 0l;
    private Long diskUsageLockedBuilds = 0l;
    
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
    
    public void refreshGlobalInformation(){
        diskUsageBuilds = 0l;
            diskUsageWorkspaces = 0l;
            diskUsageJobsWithoutBuilds = 0l;
            diskUsageLockedBuilds = 0l;
        for(Item item: Jenkins.getInstance().getItems()){
            if(item instanceof AbstractProject){
                AbstractProject project = (AbstractProject) item;
                ProjectDiskUsageAction action = (ProjectDiskUsageAction) project.getAction(ProjectDiskUsageAction.class);
                diskUsageBuilds += action.getBuildsDiskUsage().get("all");
                diskUsageWorkspaces += action.getAllDiskUsageWorkspace();
                diskUsageJobsWithoutBuilds += action.getAllDiskUsageWithoutBuilds();
                diskUsageLockedBuilds += action.getBuildsDiskUsage().get("locked");
            }
        }
    }
    
    public Long getCashedGlobalBuildsDiskUsage(){
        return diskUsageBuilds;
    }
    
    public Long getCashedGlobalJobsDiskUsage(){
        return (diskUsageBuilds + diskUsageJobsWithoutBuilds);
    }
    
    public Long getCashedGlobalJobsWithoutBuildsDiskUsage(){
        return diskUsageJobsWithoutBuilds;
    }
    
    public Long getCashedGlobalLockedBuildsDiskUsage(){
     return diskUsageLockedBuilds;   
    }
    
    public Long getCashedGlobalWorkspacesDiskUsage(){
        return diskUsageWorkspaces;
    }
    
    public Long getGlobalBuildsDiskUsage(){
        refreshGlobalInformation();
        return diskUsageBuilds;
    }
    
    public Long getGlobalJobsDiskUsage(){
        refreshGlobalInformation();
        return (diskUsageBuilds + diskUsageJobsWithoutBuilds);
    }
    
    public Long getGlobalJobsWithoutBuildsDiskUsage(){
        refreshGlobalInformation();
        return diskUsageJobsWithoutBuilds;
    }
    
    public Long getGlobalWorkspacesDiskUsage(){
        refreshGlobalInformation();
        return diskUsageWorkspaces;
    }
    
    public Long getJobWorkspaceExceedSize(){
        return DiskUsageUtil.getSizeInBytes(jobWorkspaceExceedSize);
    }
    
    public String getJobWorkspaceExceedSizeInString(){
        return jobWorkspaceExceedSize;
    }
    
    public String getUnit(String unit){
        if(unit==null)
            return null;
        return unit.split(" ")[1];
    }
    
    public String getValue(String size){
        if(size==null)
            return null;
        return size.split(" ")[0];
    }
    
    public String getEmailAddress(){
        return email;
    }
    
    public boolean warningAboutExceededSize(){
        return email!=null;
    }
    
    public Long getAllJobsExceedSize(){
        return DiskUsageUtil.getSizeInBytes(allJobsSize);
    }
    
    public Long getBuildExceedSize(){
        return DiskUsageUtil.getSizeInBytes(buildSize);
    }
    
    public Long getJobExceedSize(){
        return DiskUsageUtil.getSizeInBytes(jobSize);
    }
    
    public String getAllJobsExceedSizeInString(){
        return allJobsSize;
    }
    
    public String getBuildExceedSizeInString(){
        return buildSize;
    }
    
    public String getJobExceedSizeInString(){
        return jobSize;
    }
    
    public void sendEmail(String message, String subject){
        
    }
    
    @Override
    public XmlFile getConfigXml(){
        return new XmlFile(Jenkins.XSTREAM,
                new File(Jenkins.getInstance().getRootDir(),"disk-usage.xml"));
    }
    
    public BuildDiskUsageCalculationThread getBuildsDiskUsateThread(){
        return AperiodicWork.all().get(BuildDiskUsageCalculationThread.class);
    }
    
    public JobWithoutBuildsDiskUsageCalculation getJobsDiskUsateThread(){
        return AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class);
    }
    
    public WorkspaceDiskUsageCalculationThread getWorkspaceDiskUsageThread(){
       return AperiodicWork.all().get(WorkspaceDiskUsageCalculationThread.class); 
    }
   
    public int getWorkspaceTimeOut(){
        return workspaceTimeOut;
    }
    
    /**
     * @return DiskUsage for given project (shortcut for the view). Never null.
     */
    public ProjectDiskUsageAction getDiskUsage(Job project) {
        ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
        return action;
    }
    
    public String getDiskUsageInString(Long size){
        return DiskUsageUtil.getSizeString(size);
    }
    
    //Another shortcut
    public static String getProjectUrl(Job project) {
        return Util.encode(project.getAbsoluteUrl());
    }
    
    /**
     * @return Project list sorted by occupied disk space
     */
    public List getProjectList() {
        refreshGlobalInformation();
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

        List<AbstractProject> projectList = new ArrayList();
        for(Item item: Jenkins.getInstance().getItems()){
            if(item instanceof AbstractProject)
                projectList.add((AbstractProject)item);
        }
        Collections.sort(projectList, comparator);
        
        return projectList;
    }
    
     public void doDoConfigure(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException{
         Jenkins.getInstance().checkPermission(Permission.CONFIGURE);
            JSONObject form = req.getSubmittedForm();
            //workspaceTimeOut = form.getInt("countInterval");
            checkWorkspaceOnSlave = form.getBoolean("checkWorkspaceOnSlave");
            calculationBuilds = form.containsKey("calculationBuilds");
            calculationJobs = form.containsKey("calculationJobs");
            calculationWorkspace = form.containsKey("calculationWorkspace");
            countIntervalBuilds = calculationBuilds? form.getJSONObject("calculationBuilds").getString("countIntervalBuilds") : "0 */6 * * *";
            countIntervalJobs = calculationJobs? form.getJSONObject("calculationJobs").getString("countIntervalJobs") : "0 */6 * * *";
            countIntervalWorkspace = calculationWorkspace? form.getJSONObject("calculationWorkspace").getString("countIntervalWorkspace") : "0 */6 * * *";

            if(form.containsKey("warnings")){
                JSONObject warnings = form.getJSONObject("warnings");
                email = warnings.getString("email");           
                if(email!=null){
                    allJobsSize = warnings.containsKey("jobsWarning")? (warnings.getJSONObject("jobsWarning").getInt("allJobsSize") + " " + warnings.getJSONObject("jobsWarning").getString("JobsSizeUnit")) : null;
                    buildSize = warnings.containsKey("buildWarning")? (warnings.getJSONObject("buildWarning").getInt("buildSize") + " " + warnings.getJSONObject("buildWarning").getString("buildSizeUnit")) : null;
                    jobSize = warnings.containsKey("jobWarning")? (warnings.getJSONObject("jobWarning").getInt("jobSize") + " " + warnings.getJSONObject("jobWarning").getString("jobSizeUnit")) : null;
                    jobWorkspaceExceedSize = warnings.containsKey("workspaceWarning")? (warnings.getJSONObject("workspaceWarning").getInt("jobWorkspaceExceedSize") + " " + warnings.getJSONObject("workspaceWarning").getString("jobWorkspaceExceedSizeUnit")) : null;
                }
            }
            showGraph = form.getBoolean("showGraph");
			String histlen = req.getParameter("historyLength");
                        System.out.println("form " + req.getSubmittedForm());
                        System.out.println(histlen);
			if(histlen != null && !histlen.isEmpty()){
                            historyLength = Integer.parseInt(histlen);
                        }
            save();
            req.getView(this, "index.jelly").forward(req, rsp);
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
    
    public boolean getCheckWorkspaceOnSlave(){
        return checkWorkspaceOnSlave;
    }
    
    public void setCheckWorkspaceOnSlave(boolean check){
        checkWorkspaceOnSlave = check;
    }
    
     public boolean isCalculationWorkspaceEnabled(){
        return calculationWorkspace;
    }
    
    public boolean isCalculationBuildsEnabled(){
        return calculationBuilds;
    }
    
    public boolean isCalculationJobsEnabled(){
        return calculationJobs;
    }
    
    public boolean warnAboutJobWorkspaceExceedSize(){
        return jobWorkspaceExceedSize!=null;
    }
    
    public boolean warnAboutAllJobsExceetedSize(){
        return allJobsSize!=null;
    }
    
    public boolean warnAboutBuildExceetedSize(){
        return buildSize!=null;
    }
    
    public boolean warnAboutJobExceetedSize(){
        return jobSize!=null;
    }   
    
    public void doFilter(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException{
        Date older = DiskUsageUtil.getDate(req.getParameter("older"), req.getParameter("olderUnit"));
        Date younger = DiskUsageUtil.getDate(req.getParameter("younger"), req.getParameter("youngerUnit"));
        req.setAttribute("filter", "filter");
        req.setAttribute("older", older);
        req.setAttribute("younger", younger);
        
        req.getView(this, "index.jelly").forward(req, rsp);     
    }
    
    public void doConfigure(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException{
        req.getView(this, "settings.jelly").forward(req, rsp);
    }
    
    
    public Graph getOverallGraph(){
        long maxValue = 0;
        //First iteration just to get scale of the y-axis
        for (DiskUsageRecord usage : history ){
            maxValue = usage.getAllSpace();
        }

        int floor = (int) DiskUsageUtil.getScale(maxValue);
        String unit = DiskUsageUtil.getUnitString(floor);
        double base = Math.pow(1024, floor);

        DataSetBuilder<String, Date> dsb = new DataSetBuilder<String, Date>();
        DataSetBuilder<String, Date> dsb2 = new DataSetBuilder<String, Date>();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (DiskUsageRecord usage : history ) {
            Date label = usage.getDate();
            dataset.addValue(((Long) usage.getAllSpace()) / base, "free space of jobs directory", label);
            dataset.addValue(((Long) usage.getJobsDiskUsage()) / base, "all jobs", label);
            dataset.addValue(((Long) usage.getBuildsDiskUsage()) / base, "all builds", label);
            dsb2.add(((Long) usage.getWorkspacesDiskUsage()) / base, "workspaces", label);
            
        }
            return new DiskUsageGraph(dataset, unit, dsb2.build());
        }  
    
    public void doRecordDiskUsage(StaplerRequest req, StaplerResponse res) throws ServletException, IOException, Exception {
        getBuildsDiskUsateThread().doRun();
        getJobsDiskUsateThread().doRun();
        getWorkspaceDiskUsageThread().doRun();
        res.forwardToPreviousPage(req);
    }
    
}
