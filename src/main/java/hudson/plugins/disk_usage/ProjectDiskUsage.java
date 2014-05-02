package hudson.plugins.disk_usage;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.Job;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Lucie Votypkova
 */
public class ProjectDiskUsage implements Saveable{
    
    private transient Job job;
    protected Long diskUsageWithoutBuilds = 0l;
    protected Map<String,Map<String,Long>> slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
    
     public XmlFile getConfigFile(){
        return new XmlFile(new File(job.getRootDir(), "disk-usage.xml"));
    }
     
     public void setProject(Job job){
         this.job = job;
     }
    
    public synchronized void save() {
        if(BulkChange.contains(this))   return;
        try {
            getConfigFile().write(this);
            SaveableListener.fireOnChange(this, getConfigFile());
        } catch (IOException e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to save "+getConfigFile(),e);
        }
    }
    
    public synchronized void load(){
        XmlFile file = getConfigFile();
        if(!file.exists())
            return;
        try {
            file.unmarshal(this);
        } catch (IOException e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to load "+file, e);
        }
        
    }
    
}
