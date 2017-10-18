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

    private String countIntervalWorkspace ="0 */6 * * *";

    private boolean checkWorkspaceOnSlave = false;

    private String jobWorkspaceExceedSize;

    private int timeoutWorkspace = 5;

    public WorkspaceConfiguration(String jobWorkspaceExceedSize, boolean checkWorkspaceOnSlave, String countIntervalWorkspace, int timeoutWorkspace){
        this.countIntervalWorkspace = countIntervalWorkspace;
        this.checkWorkspaceOnSlave = checkWorkspaceOnSlave;
        this.jobWorkspaceExceedSize = jobWorkspaceExceedSize;
        this.timeoutWorkspace = timeoutWorkspace;
    }

    public WorkspaceConfiguration(String jobWorkspaceExceedSize, String countIntervalWorkspace, int timeoutWorkspace){
        this.checkWorkspaceOnSlave = false;
        this.jobWorkspaceExceedSize = jobWorkspaceExceedSize;
        this.countIntervalWorkspace = countIntervalWorkspace;
        this.timeoutWorkspace = timeoutWorkspace;
    }

    public WorkspaceConfiguration(String countIntervalWorkspace, int timeoutWorkspace){
        this.countIntervalWorkspace = countIntervalWorkspace;
        this.checkWorkspaceOnSlave = false;
        this.jobWorkspaceExceedSize = null;
        this.timeoutWorkspace = timeoutWorkspace;
    }

    public WorkspaceConfiguration(){
        countIntervalWorkspace = null;
        checkWorkspaceOnSlave = false;
        jobWorkspaceExceedSize = null;
        this.timeoutWorkspace = 5;
    }

    public void setCheckWorkspaceOnSlave(boolean checkWorkspaceOnSlave){
        this.checkWorkspaceOnSlave = checkWorkspaceOnSlave;
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

    public boolean isCheckWorkspaceOnSlave(){
        return checkWorkspaceOnSlave;
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

    public static WorkspaceConfiguration configureWorkspacesCalculation(JSONObject form, WorkspaceConfiguration oldConfiguration){
        boolean check = form.getBoolean("checkWorkspaceOnSlave");
        int timeout = form.getInt("timeoutWorkspace");
        String warning = form.containsKey("workspaceWarning")? (form.getJSONObject("workspaceWarning").getInt("jobWorkspaceExceedSize") + " " + form.getJSONObject("workspaceWarning").getString("jobWorkspaceExceedSizeUnit")) : null;
        String recalculation = null;
        if(form.containsKey("calculationWorkspace")){
            recalculation = form.getJSONObject("calculationWorkspace").getString("countIntervalWorkspace");
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
