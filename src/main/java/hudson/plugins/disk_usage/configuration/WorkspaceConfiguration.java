package hudson.plugins.disk_usage.configuration;

import hudson.model.AperiodicWork;
import hudson.plugins.disk_usage.JobWithoutBuildsDiskUsageCalculation;
import hudson.plugins.disk_usage.WorkspaceDiskUsageCalculationThread;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lvotypko on 10/3/17.
 */
public class WorkspaceConfiguration {

    private String countIntervalWorkspace ="0 1 * * 7";

    private boolean checkWorkspaceOnAgent = false;

    private String jobWorkspaceExceedSize;

    private int timeoutWorkspace = 5;

    public WorkspaceConfiguration(String jobWorkspaceExceedSize, boolean checkWorkspaceOnAgent, String countIntervalWorkspace, int timeoutWorkspace){
        this.countIntervalWorkspace = countIntervalWorkspace;
        this.checkWorkspaceOnAgent = checkWorkspaceOnAgent;
        this.jobWorkspaceExceedSize = jobWorkspaceExceedSize;
        this.timeoutWorkspace = timeoutWorkspace;
    }

    public WorkspaceConfiguration(String jobWorkspaceExceedSize, String countIntervalWorkspace, int timeoutWorkspace){
        this.checkWorkspaceOnAgent = false;
        this.jobWorkspaceExceedSize = jobWorkspaceExceedSize;
        this.countIntervalWorkspace = countIntervalWorkspace;
        this.timeoutWorkspace = timeoutWorkspace;
    }

    public WorkspaceConfiguration(String countIntervalWorkspace, int timeoutWorkspace){
        this.countIntervalWorkspace = countIntervalWorkspace;
        this.checkWorkspaceOnAgent = false;
        this.jobWorkspaceExceedSize = null;
        this.timeoutWorkspace = timeoutWorkspace;
    }

    public WorkspaceConfiguration(){
        countIntervalWorkspace = null;
        checkWorkspaceOnAgent = false;
        jobWorkspaceExceedSize = null;
        timeoutWorkspace = 5;
    }

    public void setCheckWorkspaceOnAgent(boolean checkWorkspaceOnAgent){
        this.checkWorkspaceOnAgent = checkWorkspaceOnAgent;
    }

    public boolean isWarningSent(){
        return jobWorkspaceExceedSize!=null;
    }


    public boolean isWorskpaceSizeRecalculated(){
        return countIntervalWorkspace!=null;
    }

    public boolean isExceededSizeSet(){
        return jobWorkspaceExceedSize!=null;
    }

    public String getWorskpaceExceedSize(){
        return jobWorkspaceExceedSize;
    }

    public boolean isCheckWorkspaceOnAgent(){
        return checkWorkspaceOnAgent;
    }

    public int getTimeout(){
        return timeoutWorkspace;
    }

    public String getCalculationIntervalWorkspace(){
        return countIntervalWorkspace;
    }

    public void disableRecalculation(){
        countIntervalWorkspace = null;
    }

    public void enableRecalculation(){
        countIntervalWorkspace = "0 */6 * * *";
    }

    public void setTimeOut(int timeoutWorkspace){
        this.timeoutWorkspace = timeoutWorkspace;
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

    public static WorkspaceConfiguration configureWorkspacesCalculation(JSONObject form, WorkspaceConfiguration oldConfiguration){
        if(form==null || form.isNullObject()){
            return null;
        }
        System.out.println("calculation workspaces " + form);
        boolean check = form.getBoolean("checkWorkspaceOnAgent");
        int timeout = form.getInt("timeoutWorkspace");
        String warning = form.containsKey("workspaceWarning")? (form.getJSONObject("workspaceWarning").getInt("jobWorkspaceExceedSize") + " " + form.getJSONObject("workspaceWarning").getString("jobWorkspaceExceedSizeUnit")) : null;
        String recalculation = null;
        if(form.containsKey("recalculationWorkspace")){
            recalculation = form.getJSONObject("recalculationWorkspace").getString("countIntervalWorkspaces");
            if(oldConfiguration==null || !oldConfiguration.countIntervalWorkspace.equals(recalculation)){
                AperiodicWork.all().get(WorkspaceDiskUsageCalculationThread.class).reschedule();
            }
        }
        else{
            if(oldConfiguration!=null && oldConfiguration.countIntervalWorkspace!=null){
                AperiodicWork.all().get(WorkspaceDiskUsageCalculationThread.class).cancel();
            }
        }
        return new WorkspaceConfiguration(warning, check, recalculation, timeout);
    }

    public static WorkspaceConfiguration getMediumPerformanceConfiguration(){
        return new WorkspaceConfiguration(null, false, "0 1 * * 7", 5);
    }

    public static WorkspaceConfiguration getHighPerformanceConfiguration() {
        return new WorkspaceConfiguration(null, false, "0 */6 * * *", 10);
    }

}
