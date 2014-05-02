package hudson.plugins.disk_usage;

/**
 *
 * @author dvrzalik
 * Due to backward compatibility
 */
@Deprecated
public class DiskUsage {
    
    public DiskUsage() {}
    
    public DiskUsage(Long buildUsage, Long wsUsage){
        this.buildUsage = buildUsage;
        this.wsUsage = wsUsage;
    }
    
    Long buildUsage;
    Long wsUsage;
    
}
