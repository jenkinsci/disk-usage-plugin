package hudson.plugins.disk_usage;

public class DiskUsageSum extends DiskUsage {

	long freeDiskSpace = 0;
	
	public long getFreeDiskSpace() {
	        return freeDiskSpace;
	    }
	
	public DiskUsageSum() {
		super();
    }

    public DiskUsageSum(long buildDiskUsage, long wsDiskUsage) {
        super(buildDiskUsage, wsDiskUsage);
    }
	
}
