package hudson.plugins.disk_usage.configuration;

import hudson.model.AperiodicWork;
import hudson.plugins.disk_usage.BuildDiskUsageCalculationThread;
import net.sf.json.JSONObject;

/**
 * Created by lvotypko on 10/3/17.
 */
public class BuildConfiguration {

    private String countIntervalBuilds = "0 1 * * 7";

    private boolean exactInfoAboutBuilds = false;

    public BuildConfiguration(boolean exactInfoAboutBuilds, String countIntervalBuilds){
        this.countIntervalBuilds = countIntervalBuilds;
        this.exactInfoAboutBuilds = exactInfoAboutBuilds;
    }

    public BuildConfiguration(){
        exactInfoAboutBuilds = false;
        countIntervalBuilds = null;
    }

    public String getDefaultCountInterval(){
        return "0 1 * * 7";
    }


    public boolean isInfoAboutBuildsExact(){
        return exactInfoAboutBuilds;
    }

    public boolean isBuildsCalculated(){
        return countIntervalBuilds!=null;
    }

    public boolean areBuildsRecalculated(){
        return countIntervalBuilds!=null;
    }

    public String getCalculationInterval(){
        return countIntervalBuilds;
    }

    public void enableRecalculation(){
        countIntervalBuilds = "0 */6 * * *";
    }

    public void disableRecalculation(){
        countIntervalBuilds = null;
    }


    public static BuildConfiguration configureBuildsCalculation(JSONObject form, BuildConfiguration oldConfiguration){
        if(form==null || form.isNullObject()){
            AperiodicWork.all().get(BuildDiskUsageCalculationThread.class).cancel();
            return null;
        }
        String calculationInterval = form.getString("countIntervalBuilds");
        boolean exactly = form.getBoolean("exactInfo");
        if(oldConfiguration==null || oldConfiguration.countIntervalBuilds!=null && !calculationInterval.equals(oldConfiguration.countIntervalBuilds)){
           AperiodicWork.all().get(BuildDiskUsageCalculationThread.class).reschedule();
        }
        return new BuildConfiguration(exactly, calculationInterval);
    }

    public static BuildConfiguration getMediumPerformanceConfiguration(){
        return new BuildConfiguration(false, "0 1 * * 7");
    }

    public static BuildConfiguration getHighPerformanceConfiguration(){
        return new BuildConfiguration(true, "0 */6 * * *");
    }


}
