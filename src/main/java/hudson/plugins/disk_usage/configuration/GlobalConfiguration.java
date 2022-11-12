package hudson.plugins.disk_usage.configuration;

import hudson.model.AperiodicWork;
import hudson.plugins.disk_usage.DiskUsageProjectActionFactory;
import hudson.plugins.disk_usage.unused.DiskUsageNotUsedDataCalculationThread;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by lvotypko on 10/3/17.
 */
public class GlobalConfiguration {

    private JobConfiguration jobConfiguration;

    private WorkspaceConfiguration workspaceConfiguration;

    private String countNotUsedData ="0 */6 * * *";

    private String email;

    private boolean showFreeSpaceForJobDirectory = true;


    private int historyLength = 183;


    public GlobalConfiguration(String email, boolean showFreeSpaceForJobDirectory, int historyLength){
        jobConfiguration = null;
        workspaceConfiguration = null;
        countNotUsedData = null;
        this.email = email;
        this.showFreeSpaceForJobDirectory = showFreeSpaceForJobDirectory;
    }

    public GlobalConfiguration(String email, boolean showFreeSpaceForJobDirectory, int historyLength, JobConfiguration configuration, WorkspaceConfiguration workspaceConfiguration, String countNotUsedData ){
        this.jobConfiguration = configuration;
        this.workspaceConfiguration = workspaceConfiguration;
        this.countNotUsedData = countNotUsedData;
        this.email = email;
        this.showFreeSpaceForJobDirectory = showFreeSpaceForJobDirectory;
        this.historyLength = historyLength;
    }

    public void setHistoryLength(int length){
        this.historyLength = length;
    }

    public boolean isDiskUsageCalculatedPerJobs(){
        return jobConfiguration!=null;
    }

    public boolean isDiskUsageCalculatedPerBuilds(){
        if(isDiskUsageCalculatedPerJobs()){
            return jobConfiguration.areBuilsCalculatedSeparately();
        }
        return false;
    }

    public boolean isDiskUsageCalculatedForWorkspace(){
        return workspaceConfiguration!=null;
    }

    public JobConfiguration getJobConfiguration(){
        if(jobConfiguration == null){
            //retrun empty job config
            return new JobConfiguration();
        }
        return jobConfiguration;
    }

    public WorkspaceConfiguration getWorkspaceConfiguration(){
        if(workspaceConfiguration == null){
            return new WorkspaceConfiguration();
        }
        return workspaceConfiguration;
    }

    public String getCalculationIntervalForNonUsedData(){
        return countNotUsedData;
    }

    public boolean areNonUsedDataCalculated(){
        if(jobConfiguration==null){
            return false;
        }
        return countNotUsedData!=null;
    }

    public String getEmail(){
        return email;
    }

    public boolean showFreeSpaceForJobDirectory(){
        return showFreeSpaceForJobDirectory;
    }

    public int getHistoryLength(){
        return historyLength;
    }

    public static GlobalConfiguration configureJobsCalculation(JSONObject form, GlobalConfiguration oldConfiguration) {
        String email = form.getString("email");
        int histlen = form.getInt("historyLength");
        boolean freeSpaceForJobDirectory = form.getBoolean("showFreeSpaceForJobDirectory");
        String recalculation = null;
        if (form.containsKey("calculationNotUsedData")) {
            recalculation = form.getJSONObject("calculationNotUsedData").getString("countIntervalNotUsedData");
            if (oldConfiguration.countNotUsedData != null && !oldConfiguration.countNotUsedData.equals(recalculation)) {
                AperiodicWork.all().get(DiskUsageNotUsedDataCalculationThread.class).reschedule();
            }
        } else {
            if (oldConfiguration.countNotUsedData != null) {
                AperiodicWork.all().get(DiskUsageNotUsedDataCalculationThread.class).cancel();
            }
        }

        JobConfiguration jobConfiguration = JobConfiguration.configureJobsCalculation(form.getJSONObject("calculationPerJob"), oldConfiguration.jobConfiguration);
        WorkspaceConfiguration workspaceConfiguration = WorkspaceConfiguration.configureWorkspacesCalculation(form.getJSONObject("calculationWorkspaces"), oldConfiguration.workspaceConfiguration);
        return new GlobalConfiguration(email, freeSpaceForJobDirectory, histlen, jobConfiguration, workspaceConfiguration, recalculation);
    }


    public static GlobalConfiguration getLowestPerformanceConfiguration(){
        return new GlobalConfiguration(null, true, 100, null, null, null);
    }

    public static GlobalConfiguration getLowPerformanceConfiguration(){
        return new GlobalConfiguration(null, true, 100, JobConfiguration.getLowPerformanceConfiguration(), null, null);
    }

    public static GlobalConfiguration getMediumPerformanceConfiguration(){
        return new GlobalConfiguration(null, true, 100, JobConfiguration.getMediumPerformanceConfiguration(), WorkspaceConfiguration.getMediumPerformanceConfiguration(), null);
    }

    public static GlobalConfiguration getHighPerformanceConfiguration(){
        return new GlobalConfiguration(null, true, 100, JobConfiguration.getHighPerformanceConfiguration(), WorkspaceConfiguration.getHighPerformanceConfiguration(), "0 */6 * * *");
    }




    private static GlobalConfiguration LOW_PERFORMANCE = getLowPerformanceConfiguration();

    private static GlobalConfiguration LOWEST_PERFORMANCE = getLowestPerformanceConfiguration();
    private static GlobalConfiguration MEDIUM_PERFORMANCE = getMediumPerformanceConfiguration();
    private static GlobalConfiguration HIGHT_PERFORMANCE = getHighPerformanceConfiguration();

    public enum ConfigurationType {

        LOWEST() {
            public GlobalConfiguration getConfiguration(){
                return GlobalConfiguration.LOWEST_PERFORMANCE;
            }

            public String getName(){
                return "Lowest";
            }

            public String getValue(){
                return "LOWEST";
            }
        },

        LOW() {
            public GlobalConfiguration getConfiguration(){
                return GlobalConfiguration.LOW_PERFORMANCE;
            }

            public String getName(){
                return "Low";
            }

            public String getValue(){
                return "LOW";
            }
        },

        MEDIUM {
            public GlobalConfiguration getConfiguration(){
                return GlobalConfiguration.MEDIUM_PERFORMANCE;
            }

            public String getName(){
                return "Medium";
            }

            public String getValue(){
                return "MEDIUM";
            }
        },

        HIGH {
            public GlobalConfiguration getConfiguration() {
                return GlobalConfiguration.HIGHT_PERFORMANCE;
            }

            public String getName(){
                return "High";
            }

            public String getValue(){
                return "HIGH";
            }
        },

        CUSTOM {
            public GlobalConfiguration getConfiguration() {
               return DiskUsageProjectActionFactory.DESCRIPTOR.getCustomConfiguration();
            }

            public String getName(){
                return "Custom";
            }

            public String getValue(){
                return "CUSTOM";
            }
        };

        public abstract GlobalConfiguration getConfiguration();

        public abstract String getName();

        public abstract String getValue();
    }


}
