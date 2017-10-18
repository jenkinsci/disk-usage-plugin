package hudson.plugins.disk_usage.configuration;

import hudson.model.AperiodicWork;
import hudson.plugins.disk_usage.BuildDiskUsageCalculationThread;
import net.sf.json.JSONObject;

/**
 * Created by lvotypko on 10/3/17.
 */
public class BuildConfiguration {

    private String countIntervalBuilds = "0 */6 * * *";

    private boolean exactInfoAboutBuilds = false;

    public BuildConfiguration(boolean exactInfoAboutBuilds, String countIntervalBuilds){
        this.countIntervalBuilds = countIntervalBuilds;
        this.exactInfoAboutBuilds = exactInfoAboutBuilds;
    }

    public BuildConfiguration(){
        exactInfoAboutBuilds = false;
        countIntervalBuilds = null;
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
        System.err.println("builds " + form);
        String calculation = form.getString("build");
        if(calculation.equals("onlyInfoPerBuild")){
            BuildConfiguration configuration = new BuildConfiguration();
            if(oldConfiguration.countIntervalBuilds != null){
                AperiodicWork.all().get(BuildDiskUsageCalculationThread.class).cancel();
            }
            return configuration;
        }
        if(calculation.equals("calculateBuild")){
            JSONObject warning = form.getJSONObject("buildWarning");
            String size = null;
            if(warning!=null){
                size = warning.getInt("buildSize") + " " + warning.getString("buildSizeUnit");
            }
            if(form.getBoolean("calculationBuilds")) {
                BuildConfiguration configuration = new BuildConfiguration(form.getBoolean("exactInfo"), form.getString("countIntervalBuilds"));
                if(oldConfiguration==null || oldConfiguration.countIntervalBuilds!=null && !configuration.countIntervalBuilds.equals(oldConfiguration.countIntervalBuilds)){
                    AperiodicWork.all().get(BuildDiskUsageCalculationThread.class).reschedule();
                }
                return configuration;
            }
            else{
                BuildConfiguration configuration = new BuildConfiguration(false, null);
                if(oldConfiguration.countIntervalBuilds!=null){
                    AperiodicWork.all().get(BuildDiskUsageCalculationThread.class).cancel();
                }
            }
        }
        return null;
    }

    public static BuildConfiguration getLowPerformanceConfiguration(){
        return new BuildConfiguration();
    }

    public static BuildConfiguration getMedionPerformanceConfiguration(){
        return new BuildConfiguration(false, "0 1 * * 7");
    }

    public static BuildConfiguration getHighPerformanceConfiguration(){
        return new BuildConfiguration(true, "0 */6 * * *");
    }


}
