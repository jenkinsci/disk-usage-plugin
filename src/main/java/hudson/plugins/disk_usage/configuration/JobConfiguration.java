package hudson.plugins.disk_usage.configuration;

import hudson.model.AperiodicWork;
import hudson.model.Job;
import hudson.plugins.disk_usage.DiskUsageUtil;
import hudson.plugins.disk_usage.JobWithoutBuildsDiskUsageCalculation;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Created by lvotypko on 10/3/17.
 */
public class JobConfiguration {

    private String countIntervalJobs = "0 1 * * 7";

    private String jobSize;

    private String allJobsSize;

    private String buildSize;

    private BuildConfiguration buildConfiguration;

    private boolean showGraph;

    private List<String> excludedJobs = new ArrayList<String>();

    public JobConfiguration(List<String> excludedJobs, String buildSize, String jobSize, String allJobsSize, String countIntervalJobs, BuildConfiguration buildConfiguration, boolean showGraph){
        this.jobSize = jobSize;
        this.allJobsSize = allJobsSize;
        this.countIntervalJobs = countIntervalJobs;
        this.buildConfiguration = buildConfiguration;
        this.showGraph = showGraph;
        this.excludedJobs = excludedJobs;
        this.buildSize = buildSize;
    }

    public void enableRecalculation(){
        countIntervalJobs = "0 */6 * * *";
    }

    public void disableRecalculation(){
        countIntervalJobs = null;
    }

    public void setCalculationInterval(String value){
        countIntervalJobs = value;
    }

    public JobConfiguration(List<String> excludedJobs, String buildSize,  String jobSize, String allJobsSize, String countIntervalJobs){
        this.jobSize = jobSize;
        this.allJobsSize = allJobsSize;
        this.countIntervalJobs = countIntervalJobs;
        buildConfiguration = null;
        showGraph = false;
        this.excludedJobs = excludedJobs;
        this.buildSize = buildSize;
    }

    public JobConfiguration(){
        this.excludedJobs = new ArrayList<String>();
        this.jobSize = null;
        this.allJobsSize = null;
        this.buildSize = null;
        this.countIntervalJobs = "0 1 * * 7";
        buildConfiguration = null;
        showGraph = false;
    }

    public boolean areBuilsCalculatedSeparately(){
        return buildConfiguration!=null;
    }

    public boolean areJobsRecalculated(){
        return countIntervalJobs!=null;
    }

    public String calculationInterval(){
        return countIntervalJobs;
    }

    public String getJobSize(){
        return jobSize;
    }

    public String getAllJobsSize(){
        return allJobsSize;
    }

    public boolean isSentWarningAboutJobsSize(){
        return allJobsSize!=null;
    }

    public boolean isSentWarningAboutJobSize(){
        return  jobSize!=null;
    }

    public boolean isSentWarningAboutBuildSize() {
        return buildSize!=null;
    }

    public BuildConfiguration getBuildConfiguration(){
        if(buildConfiguration == null){
            return new BuildConfiguration();
        }
        return buildConfiguration;
    }

    public boolean showGraph(){
        return showGraph;
    }

    public int getJobSizeValue(){
        return Integer.decode(jobSize.split(" ")[0]);
    }

    public String getJobSizeUnit(){
        return jobSize.split(" ")[1];
    }

    public int getAllJobsSizeValue(){
        return Integer.decode(allJobsSize.split(" ")[0]);
    }

    public String getAllJobsSizeUnit(){
        return allJobsSize.split(" ")[1];
    }

    public List<String> getExcludedJobs(){
        if(excludedJobs==null){
            return Collections.EMPTY_LIST;
        }
         return excludedJobs;
    }

    public String getExcludedJobsInString(){
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String name : getExcludedJobs()) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append(name);
        }
        return builder.toString();
    }

    public void removeExcludedJob(String name){
        if(excludedJobs==null){
            return;
        }
        excludedJobs.remove(name);
    }

    public void renameExcludedJob(String oldName, String newName){
        if(excludedJobs==null){
            return;
        }
        if(excludedJobs.contains(oldName)) {
            excludedJobs.remove(oldName);
            excludedJobs.add(newName);
        }
    }

    public int getBuildSizeValue(){
        return Integer.decode(buildSize.split(" ")[0]);
    }

    public String getBuildSizeUnit(){
        return buildSize.split(" ")[1];
    }

    public String getBuildSize(){
        return buildSize;
    }

    public void setExcludedJobs(List<String> jobs){
        excludedJobs = jobs;
    }

    public void setShowGraph(boolean showGraph){
        this.showGraph=showGraph;
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

    public static JobConfiguration configureJobsCalculation(JSONObject form, JobConfiguration oldConfiguration){
        if(form==null || form.isNullObject()){
            if(oldConfiguration!=null){
                AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class).cancel();
            }
            return null;
        }
        String allJobsSizeWarning = form.containsKey("jobsWarning")? (form.getJSONObject("jobsWarning").getInt("allJobsSize") + " " + form.getJSONObject("jobsWarning").getString("jobsSizeUnit")) : null;
        String jobSizeWarning = form.containsKey("jobWarning")? (form.getJSONObject("jobWarning").getInt("jobSize") + " " + form.getJSONObject("jobWarning").getString("jobSizeUnit")) : null;
        String buildSizeWarning = form.containsKey("buildWarning")? (form.getJSONObject("buildWarning").getInt("buildSize") + " " + form.getJSONObject("buildWarning").getString("buildSizeUnit")) : null;

        boolean graph = form.getBoolean("showGraph");

        String excluded = form.getString("excludedJobs");
        List<String> jobs = DiskUsageUtil.parseExcludedJobsFromString(excluded);
        String recalculationInterval = null;
         recalculationInterval = form.getString("countIntervalJobs");
         if(oldConfiguration == null || oldConfiguration.countIntervalJobs == null || !oldConfiguration.countIntervalJobs.equals(recalculationInterval)){
             AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class).reschedule();
         }
        else{
            if(oldConfiguration!=null && oldConfiguration.countIntervalJobs!=null){
                AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class).cancel();
            }
        }
        BuildConfiguration buildConfig = BuildConfiguration.configureBuildsCalculation(form.getJSONObject("calculationPerBuild"), oldConfiguration!=null? oldConfiguration.buildConfiguration:null);
        return new JobConfiguration(jobs, buildSizeWarning, jobSizeWarning, allJobsSizeWarning, recalculationInterval, buildConfig, graph);
    }


    public static JobConfiguration getLowPerformanceConfiguration(){
        return new JobConfiguration(null, null, null, null, "0 1 * * 7", null, true);
    }

    public static JobConfiguration getMediumPerformanceConfiguration(){
        return new JobConfiguration(null, null, null, null, "0 1 * * 7", BuildConfiguration.getMediumPerformanceConfiguration(), true);
    }

    public static JobConfiguration getHighPerformanceConfiguration() {
        return new JobConfiguration(null, null, null, null, "0 */6 * * *", BuildConfiguration.getHighPerformanceConfiguration(), true);
    }




}
