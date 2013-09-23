package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.*;

import hudson.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class DiskUsageProjectActionFactory extends TransientProjectActionFactory implements Describable<DiskUsageProjectActionFactory> {

    @Override
    public Collection<? extends Action> createFor(AbstractProject job) {
        return Collections.singleton(new ProjectDiskUsageAction(job));
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public Descriptor<DiskUsageProjectActionFactory> getDescriptor() {
        return DESCRIPTOR;
    }

    public static class DescriptorImpl extends Descriptor<DiskUsageProjectActionFactory> {

        public DescriptorImpl() {
            load();
        }

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
    
        private Long diskUsageBuilds = 0l;
        private Long diskUsageJobsWithoutBuilds = 0l;
        private Long diskUsageWorkspaces = 0l;
        private Long diskUsageLockedBuilds = 0l;
    
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

        // Timeout for a single Project's workspace analyze (in mn)
        private int timeoutWorkspace = 5;
        
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
    
    public Long getJobWorkspaceExceedSize(){
        return DiskUsageUtil.getSizeInBytes(jobWorkspaceExceedSize);
    }
    
    public String getJobWorkspaceExceedSizeInString(){
        return jobWorkspaceExceedSize;
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

        public boolean addHistory(DiskUsageOvearallGraphGenerator.DiskUsageRecord e) {
        return history.add(e);
    }


        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }


        @Override
        public DiskUsageProjectActionFactory newInstance(StaplerRequest req, JSONObject formData) {
            return new DiskUsageProjectActionFactory();
        }


        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            Jenkins.getInstance().checkPermission(Permission.CONFIGURE);
            JSONObject form;
            try {
                form = req.getSubmittedForm();
            } catch (ServletException ex) {
                Logger.getLogger(DiskUsageProjectActionFactory.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
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
			if(histlen != null && !histlen.isEmpty()){
                            historyLength = Integer.parseInt(histlen);
                        }
            save();
            return true;
        }

        public int getTimeoutWorkspace() {
            return timeoutWorkspace;
        }

        public void setTimeoutWorkspace(Integer timeoutWorkspace) {
            this.timeoutWorkspace = timeoutWorkspace;
        }
    }


}
