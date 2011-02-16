package hudson.plugins.disk_usage;

import java.text.DecimalFormat;

/**
 * 
 * @author dvrzalik
 */
public class DiskUsage {
	
    long buildUsage = 0;
    long wsUsage = 0;

    // prediction fields
    long predictedNeededSpace = 0;
    int predictedNumberOfBuilds = 0;
    boolean diskManagementNotFullyConfigured = false;

    public DiskUsage() {
    }

    public DiskUsage(long buildDiskUsage, long wsDiskUsage) {
        buildUsage = buildDiskUsage;
        wsUsage = wsDiskUsage;
    }

    public long getBuildUsage() {
        return buildUsage;
    }

    public long getWsUsage() {
        return wsUsage;
    }

    public boolean isDiskManagementNotFullyConfigured() {
        return diskManagementNotFullyConfigured;
    }

    public long getPredictedNeededSpace() {
        return predictedNeededSpace;
    }

    public String getPredictedNumberOfBuilds() {
        if (predictedNumberOfBuilds > 0)
            return new Integer(predictedNumberOfBuilds).toString();
        else
            return "?";
    }
    
    public String getBuildUsageString() {
        return getSizeString(buildUsage);
    }

    public String getWsUsageString() {
        return getSizeString(wsUsage);
    }

    public static final String getSizeString(Long size) {
        if (size == null || size <= 0) {
            return "-";
        }

        int floor = (int) getScale(size);
        floor = Math.min(floor, 4);
        double base = Math.pow(1024, floor);
        String unit = getUnitString(floor);
        
        //return Math.round(size / base) + unit; // why rounding
        return new DecimalFormat("0.00").format(new Double(size) / base) + unit;
    }

    public static final double getScale(long number) {
        return Math.floor(Math.log(number) / Math.log(1024));
    }

    public static String getUnitString(int floor) {
        String unit = "";
        switch (floor) {
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
}
