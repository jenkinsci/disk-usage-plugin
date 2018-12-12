package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.*;
import hudson.plugins.disk_usage.configuration.BuildConfiguration;
import hudson.plugins.disk_usage.configuration.GlobalConfiguration;
import hudson.plugins.disk_usage.configuration.JobConfiguration;
import hudson.plugins.disk_usage.configuration.WorkspaceConfiguration;
import hudson.security.Permission;

import java.io.IOException;
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
 * @author: <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class DiskUsageProjectActionFactory extends TransientProjectActionFactory implements Describable<DiskUsageProjectActionFactory> {

    @Override
    public Collection<? extends Action> createFor(AbstractProject job) {
        ProjectDiskUsageAction action = new ProjectDiskUsageAction(job);
        return Collections.singleton(action);
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

        protected GlobalConfiguration configuration = null;

        private GlobalConfiguration.ConfigurationType type = GlobalConfiguration.ConfigurationType.LOW;

        private Long diskUsageBuilds = 0l;
        private Long diskUsageJobsWithoutBuilds = 0l;
        private Long diskUsageWorkspaces = 0l;
        private Long diskUsageLockedBuilds = 0l;


        List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> history = new LinkedList<DiskUsageOvearallGraphGenerator.DiskUsageRecord>(){
				private static final long serialVersionUID = 1L;

				@Override
				public boolean add(DiskUsageOvearallGraphGenerator.DiskUsageRecord e) {
					boolean ret = super.add(e);
					if(ret && this.size() > configuration.getHistoryLength()){
						this.removeRange(0, this.size() - configuration.getHistoryLength());
					}
					return ret;
				}
			};

        @Deprecated
        private String countIntervalBuilds;

        @Deprecated
        private Boolean calculationBuilds = true;

        @Deprecated
        private String countIntervalJobs;

        @Deprecated
        private Boolean calculationJobs = true;

        @Deprecated
        private String countIntervalWorkspace;

        @Deprecated
        private Boolean calculationWorkspace = true;

        @Deprecated
        private Boolean checkWorkspaceOnSlave = false;

        @Deprecated
        private String countNotUsedData;

        @Deprecated
        private Boolean calculationNotUsedData = false;

        @Deprecated
        private String email;

        @Deprecated
        private String jobSize;

        @Deprecated
        private String buildSize;

        @Deprecated
        private String allJobsSize;

        @Deprecated
        private String jobWorkspaceExceedSize;

        @Deprecated
        private Boolean showFreeSpaceForJobDirectory = true;

        @Deprecated
        private List<String> excludedJobs = new ArrayList<String>();

        @Deprecated
        private Boolean showGraph = true;

        @Deprecated
        private Integer historyLength = 183;

        @Deprecated
        private Integer timeoutWorkspace = 5;

        public Object readResolve(){
            if(countIntervalJobs!=null || countIntervalWorkspace!=null || countNotUsedData !=null || countIntervalBuilds!=null) {
                //old setting
                BuildConfiguration buildConfiguration = null;
                JobConfiguration jobConfiguration = null;
                WorkspaceConfiguration workspaceConfiguration = null;
                type = GlobalConfiguration.ConfigurationType.CUSTOM;
                if (calculationBuilds) {
                    buildConfiguration = new BuildConfiguration(true, countIntervalBuilds);
                } else {
                    buildConfiguration = new BuildConfiguration(false, null);
                }
                if (calculationJobs) {
                    jobConfiguration = new JobConfiguration(excludedJobs,buildSize, jobSize, allJobsSize, countIntervalJobs, buildConfiguration, showGraph);
                } else {
                    jobConfiguration = new JobConfiguration(excludedJobs,buildSize, jobSize, allJobsSize, null, buildConfiguration, showGraph);
                }
                if (calculationWorkspace) {
                    workspaceConfiguration = new WorkspaceConfiguration(jobWorkspaceExceedSize, checkWorkspaceOnSlave, countIntervalWorkspace, timeoutWorkspace);
                } else {
                    workspaceConfiguration = new WorkspaceConfiguration(jobWorkspaceExceedSize, checkWorkspaceOnSlave, null, timeoutWorkspace);
                }
                if (calculationNotUsedData){
                    configuration = new GlobalConfiguration(email, showFreeSpaceForJobDirectory, historyLength, jobConfiguration, workspaceConfiguration, countNotUsedData);
                }
                else{
                    configuration = new GlobalConfiguration(email, showFreeSpaceForJobDirectory, historyLength, jobConfiguration, workspaceConfiguration, null);
                }
                //make null everything
                countIntervalBuilds = null;
                calculationBuilds = null;
                buildSize = null;
                calculationJobs = null;
                excludedJobs = null;
                jobSize = null;
                countIntervalJobs = null;
                showGraph = null;
                calculationWorkspace = null;
                jobWorkspaceExceedSize = null;
                checkWorkspaceOnSlave = null;
                countIntervalWorkspace = null;
                timeoutWorkspace = null;
                calculationNotUsedData = null;
                email = null;
                showFreeSpaceForJobDirectory = null;
                historyLength = null;
                countNotUsedData = null;
                save();
            }
            else{
                if(type==null) {
                    type = GlobalConfiguration.ConfigurationType.LOW;
                }
            }
            return this;
        }

        // Timeout for a single Project's workspace analyze (in mn)
        
    public Long getCachedGlobalBuildsDiskUsage(){
        return diskUsageBuilds;
    }
    
    public Long getCachedGlobalJobsDiskUsage(){
        return (diskUsageBuilds + diskUsageJobsWithoutBuilds);
    }
    
    public Long getCachedGlobalJobsWithoutBuildsDiskUsage(){
        return diskUsageJobsWithoutBuilds;
    }
    
    public Long getCachedGlobalLockedBuildsDiskUsage(){
     return diskUsageLockedBuilds;   
    }
    
    public Long getCachedGlobalWorkspacesDiskUsage(){
        return diskUsageWorkspaces;
    }

    public GlobalConfiguration getCustomConfiguration(){
        return configuration;
    }

    public GlobalConfiguration getConfiguration(){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM){
            return configuration;
        }
        else{
            return type.getConfiguration();
        }
    }

    public GlobalConfiguration.ConfigurationType getType(){
        if(type==null){
            return GlobalConfiguration.ConfigurationType.LOW;
        }
        return type;
    }

    public Long getJobWorkspaceExceedSize(){
        if(getConfiguration().isDiskUsageCalculatedForWorkspace() && getConfiguration().getWorkspaceConfiguration().isExceededSizeSet()) {
            return DiskUsageUtil.getSizeInBytes(getConfiguration().getWorkspaceConfiguration().getWorskpaceExceedSize());
        }
        return null;
    }
    
    public String getJobWorkspaceExceedSizeInString(){
        if(getConfiguration().isDiskUsageCalculatedForWorkspace() && getConfiguration().getWorkspaceConfiguration().isExceededSizeSet()) {
            return getConfiguration().getWorkspaceConfiguration().getWorskpaceExceedSize();
        }
        return null;

    }
    
    public boolean isShowGraph() {
        if(getConfiguration().isDiskUsageCalculatedPerBuilds()){
            return getConfiguration().getJobConfiguration().showGraph();
        }
        return false;
    }

    public void setShowGraph(Boolean showGraph) {
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedPerBuilds()) {
            getConfiguration().getJobConfiguration().setShowGraph(showGraph);
        }
    }

    public int getHistoryLength() {
        return getConfiguration().getHistoryLength();
    }

    public void setHistoryLength(Integer historyLength) {
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM) {
            getConfiguration().setHistoryLength(historyLength);
        }
    }

    public boolean isBuildCalculatedExactly(){
        if(getConfiguration().isDiskUsageCalculatedPerBuilds()){
            return getConfiguration().getJobConfiguration().getBuildConfiguration().isInfoAboutBuildsExact();
        }
        return false;
    }

    public List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> getHistory(){
        return history;
    }

    public String getCountIntervalForBuilds(){
    	if(isCalculationBuildsEnabled()){
            return getConfiguration().getJobConfiguration().getBuildConfiguration().getCalculationInterval();
        }
        return null;
    }
    
    public String getCountIntervalForJobs(){
        if(isCalculationJobsEnabled()){
            return getConfiguration().getJobConfiguration().calculationInterval();
        }
        return null;
    }
    
    public String getCountIntervalForWorkspaces(){

        if(getConfiguration().isDiskUsageCalculatedForWorkspace()){
            return getConfiguration().getWorkspaceConfiguration().getCalculationIntervalWorkspace();
        }
        return null;
    }
    
    public String getCountIntervalForNotUsedData(){
        return getConfiguration().getCalculationIntervalForNonUsedData();
    }
    
    public boolean getCheckWorkspaceOnSlave(){
        if(getConfiguration().isDiskUsageCalculatedForWorkspace()){
            return getConfiguration().getWorkspaceConfiguration().isCheckWorkspaceOnSlave();
        }
        return false;
    }
    
    public void setCheckWorkspaceOnSlave(boolean check){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedForWorkspace()){
            getConfiguration().getWorkspaceConfiguration().setCheckWorkspaceOnSlave(check);
        }
    }
    
    public void setExcludedJobs(List<String> excludedJobs){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedPerJobs()){
            getConfiguration().getJobConfiguration().setExcludedJobs(excludedJobs);
        }
    }
    
     public boolean isCalculationWorkspaceEnabled() {

         if (getConfiguration().isDiskUsageCalculatedForWorkspace()) {
            return getConfiguration().getWorkspaceConfiguration().isWorskpaceSizeRecalculated();
         }
         return false;
     }
    
    public boolean isCalculationBuildsEnabled(){
        if(getConfiguration().isDiskUsageCalculatedPerBuilds()){
            return getConfiguration().getJobConfiguration().getBuildConfiguration().areBuildsRecalculated();
        }
        return false;
    }
    
    public boolean isCalculationJobsEnabled(){

        if(getConfiguration().isDiskUsageCalculatedPerJobs()){
            return getConfiguration().getJobConfiguration().areJobsRecalculated();
        }
        return false;
    }
    
    public boolean isCalculationNotUsedDataEnabled(){

        if(getConfiguration().isDiskUsageCalculatedPerJobs()){
            return getConfiguration().getCalculationIntervalForNonUsedData()!=null;
        }
        return false;
    }
    
    public boolean warnAboutJobWorkspaceExceedSize(){

        if(getConfiguration().isDiskUsageCalculatedForWorkspace()){
            return getConfiguration().getWorkspaceConfiguration().getWorskpaceExceedSize()!=null;
        }
        return false;
    }
    
    public boolean warnAboutAllJobsExceetedSize(){

        if(getConfiguration().isDiskUsageCalculatedPerJobs()){
            return getConfiguration().getJobConfiguration().getAllJobsSize()!=null;
        }
        return false;
    }
    
    public boolean warnAboutBuildExceetedSize(){

        if(getConfiguration().isDiskUsageCalculatedPerBuilds()){
            return getConfiguration().getJobConfiguration().getBuildSize()!=null;
        }
        return false;
    }
    
    public boolean warnAboutJobExceetedSize(){
        if(getConfiguration().isDiskUsageCalculatedPerJobs()){
            return getConfiguration().getJobConfiguration().getJobSize()!=null;
        }
        return false;
    }   

    public String getEmailAddress(){
        return getConfiguration().getEmail();
    }
    
    public boolean warningAboutExceededSize(){
        return getEmailAddress()!=null;
    }
    
    public Long getAllJobsExceedSize(){
        if(warnAboutAllJobsExceetedSize()) {
            return DiskUsageUtil.getSizeInBytes(getConfiguration().getJobConfiguration().getAllJobsSize());
        }
        return null;
    }
    
    public Long getBuildExceedSize(){
        if(warnAboutBuildExceetedSize()) {
            return DiskUsageUtil.getSizeInBytes(getConfiguration().getJobConfiguration().getBuildSize());
        }
        return null;
    }
    
    public Long getJobExceedSize(){

        if(warnAboutAllJobsExceetedSize()) {
            return DiskUsageUtil.getSizeInBytes(getConfiguration().getJobConfiguration().getJobSize());
        }
        return null;
    }
    
    public String getAllJobsExceedSizeInString(){
        if(warnAboutAllJobsExceetedSize()) {
            return getConfiguration().getJobConfiguration().getAllJobsSize();
        }
        return null;
    }
    
    public String getBuildExceedSizeInString(){

        if(warnAboutBuildExceetedSize()) {
            return getConfiguration().getJobConfiguration().getBuildSize();
        }
        return null;
    }
    
    public String getJobExceedSizeInString(){
        if(warnAboutAllJobsExceetedSize()) {
            return getConfiguration().getJobConfiguration().getJobSize();
        }
        return null;
    }

    public boolean addHistory(DiskUsageOvearallGraphGenerator.DiskUsageRecord e) {
        boolean ok = history.add(e);
        save();
        return ok;
    }
    
    public void enableBuildsDiskUsageCalculation(){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedPerBuilds()){
            getConfiguration().getJobConfiguration().getBuildConfiguration().enableRecalculation();
        }
    }
    
    public void disableBuildsDiskUsageCalculation(){

        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedPerBuilds()){
            getConfiguration().getJobConfiguration().getBuildConfiguration().disableRecalculation();
        }
    }
    
    public void enableJobsDiskUsageCalculation(){

        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedPerJobs()){
            getConfiguration().getJobConfiguration().enableRecalculation();;
        }
    }
    
    public void disableJobsDiskUsageCalculation(){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedPerJobs()){
            getConfiguration().getJobConfiguration().disableRecalculation();
        }
    }
    
    public void enableWorkspacesDiskUsageCalculation(){

        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedForWorkspace()){
            getConfiguration().getWorkspaceConfiguration().enableRecalculation();
        }
    }
    
    public void disableWorkspacesDiskUsageCalculation(){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedForWorkspace()){
            getConfiguration().getWorkspaceConfiguration().disableRecalculation();
        }
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


    @Override
    public String getDisplayName() {
        return Messages.DisplayName();
    }


    @Override
    public DiskUsageProjectActionFactory newInstance(StaplerRequest req, JSONObject formData) {
        return new DiskUsageProjectActionFactory();
    }

    public void setType(GlobalConfiguration.ConfigurationType type, GlobalConfiguration configuration ){
        this.type = type;
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM){
            this.configuration = configuration;
        }
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
        String type = form.getJSONObject("type").getString("value");
        if(type.equals("CUSTOM")) {
            GlobalConfiguration config = GlobalConfiguration.configureJobsCalculation(form.getJSONObject("type"), getConfiguration());
            configuration = config;
            this.type = GlobalConfiguration.ConfigurationType.CUSTOM;
        }
        else{
            this.type = GlobalConfiguration.ConfigurationType.valueOf(type);
            configuration = null;
        }
        save();
        return true;
    }
    
    public void onRenameJob(String oldName, String newName){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && configuration.isDiskUsageCalculatedPerJobs()) {
            configuration.getJobConfiguration().renameExcludedJob(oldName, newName);
        }
    }
    
    public void onDeleteJob(AbstractProject project){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && configuration.isDiskUsageCalculatedPerJobs()) {
            configuration.getJobConfiguration().removeExcludedJob(project.getName());
        }
    }
    
    public boolean isExcluded(AbstractProject project){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && configuration.isDiskUsageCalculatedPerJobs()) {
            return configuration.getJobConfiguration().getExcludedJobs().contains(project.getName());
        }
        return false;
    }

    public GlobalConfiguration.ConfigurationType[] getConfigurationTypes(){
        return GlobalConfiguration.ConfigurationType.values();
     }
    
    public String getExcludedJobsInString(){
        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && configuration.isDiskUsageCalculatedPerJobs()) {
            return getConfiguration().getJobConfiguration().getExcludedJobsInString();
        }
        return "";
    }

    public int getTimeoutWorkspace() {
        if(getConfiguration().isDiskUsageCalculatedForWorkspace()){
            return getConfiguration().getWorkspaceConfiguration().getTimeout();
        }
        return 0;
    }
    
    public boolean getShowFreeSpaceForJobDirectory(){
        return getConfiguration().showFreeSpaceForJobDirectory();
    }

    public void setTimeoutWorkspace(Integer timeoutWorkspace) {

        if(type == GlobalConfiguration.ConfigurationType.CUSTOM && getConfiguration().isDiskUsageCalculatedForWorkspace()){
            getConfiguration().getWorkspaceConfiguration().setTimeOut(timeoutWorkspace);
        }
    }
}


}
