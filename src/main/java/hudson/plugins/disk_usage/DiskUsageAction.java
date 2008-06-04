package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ProminentProjectAction;

/**
 * Disk usage information holder
 * @author dvrzalik
 */
public abstract class DiskUsageAction implements ProminentProjectAction {

    public static final String getSizeString(Long size) {
        if(size == null || size <= 0) {
            return "-";
        }
       
        int floor = (int) getScale(size);
        floor = Math.min(floor, 4);
        double base = Math.pow(1024,floor);
        String unit = getUnitString(floor);
        
        return Math.round(size/base) + unit;
    }
    
    public static final double getScale(long number) {
        return Math.floor(Math.log(number)/Math.log(1024));
    }
    
    public static String getUnitString(int floor) {
        String unit = "";
        switch(floor) {
            case 0:
                unit = "B";
                break;
            case 1:
                unit = "KB";
                break;
            case 2:
                unit = "MB";
                break;
            case 3:
                unit = "GB";
                break;
            case 4:
                unit = "TB";
                break;
        }
        
        return unit;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Disk usage";
    }

    public String getUrlName() {
        return "diskUsage";
    }
}
