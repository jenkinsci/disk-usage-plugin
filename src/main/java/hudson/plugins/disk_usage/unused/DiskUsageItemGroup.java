/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.unused;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.plugins.disk_usage.DiskUsageProperty;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageItemGroup implements Saveable {
    
    private transient ItemGroup itemGroup;
    
    private Map<String,Long> notLoadedJobs = new ConcurrentHashMap<String,Long>();
    
    private String jobDirectoryName;
    
    private Long diskUsageWithoutJobs = 0L;
    
    private transient Long cachedDiskUsageWithoutBuilds = 0L;
    
    private transient Map<String,Long> caschedDiskUsageBuilds = new HashMap<String,Long>();
    
    private transient Long cachedDiskUsageWorkspaces = 0L;
    
    private transient Long cachedDiskUsageCustomWorkspaces = 0L;
    
    private transient Long cachedDiskUsageNotLoadedJobs = 0L; 
    
    private transient Long cachedDiskUsageWithoutJobs = 0L;
    
    public ItemGroup getItemGroup(){
        return itemGroup;
    }
    
    public Long getCachedDiskUsageWithoutJobs(){
        return cachedDiskUsageWithoutJobs;
    }
    
    public void setcachedDiskUsageWithoutJobs(Long size){
        this.cachedDiskUsageWithoutJobs = size;
    }
    
    public Long getCachedDiskUsageNotLoadedJobs(){
        return cachedDiskUsageNotLoadedJobs;
    }
    
    public void setCachedDiskUsageNotLoadedJobs(Long size){
        this.cachedDiskUsageNotLoadedJobs = size;
    }
    
    public Long getCachedDiskUsageCustomWorkspaces(){
        return cachedDiskUsageCustomWorkspaces;
    }
    
    public void setCachedDiskUsageCustomWorkspaces(Long size ){
        this.cachedDiskUsageCustomWorkspaces = size;
    }
    
    public Long getCachedDiskUsageWorkspaces(){
        return cachedDiskUsageWorkspaces;
    }
    
    public void setCachedDiskUsageWorkspaces(Long size){
        this.cachedDiskUsageWorkspaces = size;
    }
    
    public Long getCachedDiskUsageWithoutBuilds(){
        return cachedDiskUsageWithoutBuilds;
    }
    
    public void setCachedDiskUsageWithoutBuilds(Long size){
        this.cachedDiskUsageWithoutBuilds = size;
    }
    
    public Map<String,Long> getCaschedDiskUsageBuilds(){
        return caschedDiskUsageBuilds;
    }
    
    public void setCaschedDiskUsageBuilds(Map<String,Long> size){
        this.caschedDiskUsageBuilds = size;
    }
    
    public DiskUsageItemGroup(ItemGroup item){
        itemGroup = item;
        load();
        findJobDirectory();
        
    }
    
    public void setDiskUsageWithoutJobs(Long diskUsage){
        if(itemGroup instanceof AbstractProject){
            //not need it duplicate it is in ProjectDiskUsage
            return;
        }
        diskUsageWithoutJobs = diskUsage;
    }
    
    
    public Long getDiskUsageWithoutJobs(){
        return diskUsageWithoutJobs;
    }
//    
//    public Long getDiskUsage(){
//       if(itemGroup instanceof AbstractProject){
//            AbstractProject project = (AbstractProject) itemGroup;
//            DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
//            return property.getDiskUsage().getDiskUsage();
//        }
//        return diskUsageWithoutJobs;
//    }
    
//    public void getDiskProjectUsage(){
//        
//    }
    
    private void findJobDirectory(){
        if(jobDirectoryName == null && itemGroup.getItems().size() > 0){
            for(String dirName : getStaticJobDirectory()){
                File file = new File(itemGroup.getRootDir(), dirName);
                if(file.exists() && file.isDirectory()){
                    jobDirectoryName = dirName;
                    save();
                    return;
                }
            }
            
            //item exists but no suggested directory is home, try to investigate from item
            String jobsDirPathName = null;
            for (Item i : (Collection<Item>) itemGroup.getItems()){
                File itemFile = i.getRootDir();
                if(jobsDirPathName == null){
                    jobsDirPathName = findNearestSharedChild(i.getRootDir(), itemGroup.getRootDir()).getAbsolutePath();
                }
                else{
                    if(!itemFile.getAbsolutePath().startsWith(jobsDirPathName)){
                        //all items not share the sam directory
                        return;
                        
                    }
                }
            }
            jobDirectoryName = jobsDirPathName;
            save();
        }
    }
    
    //todo - better name for method
    private File findNearestSharedChild(File child, File parent){
        File previous = child;
        while(child!=null && !child.equals(parent)){
            previous = child;
            child = child.getParentFile();
        }
        return previous;
    }
    
    public void setSize(String file, Long size){
        notLoadedJobs.put(file, size);
    }
    
    public File getJobDirectory(){
        if(jobDirectoryName == null)
            return null;
        return new File(itemGroup.getRootDir(), jobDirectoryName);
    }
    
    
    public void addNotLoadedJob(File jobDirectory){
        if(jobDirectory.exists()){
            notLoadedJobs.put(jobDirectory.getName(), 0l);
        }
    }
    
    public void addNotLoadedJob(File jobDirectory, Long size){
        if(jobDirectory.exists()){
            notLoadedJobs.put(jobDirectory.getName(), size);
        }
    }
    
    public Long getDiskUsageOfNotLoadedJob(String directoryName){
        return notLoadedJobs.get(directoryName);
    }
    
    public Map<String,Long> getNotLoadedJobs(){
        Map<String,Long> usage = new HashMap<String,Long>();
        usage.putAll(notLoadedJobs);
        return usage;
    }
    
    public void removeJob(String directory){
        notLoadedJobs.remove(directory);
    }
    
    public Long getDiskUsageOfNotLoadedJobs(boolean cached){
        if(cached){
            return cachedDiskUsageNotLoadedJobs;
        }
        Long size = 0L;
        for(Long s : notLoadedJobs.values()){
            size += s;
        }
        return size;
    }
    
    public XmlFile getConfigFile(){
        return new XmlFile(new File(itemGroup.getRootDir(), "disk-usage-item-group.xml"));
    }

    @Override
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
        if(!file.getFile().exists()){
            return;
        }
        try {
            file.unmarshal(this);
        } catch (IOException e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to load "+file, e);
        }
    }
    
    private static List<String> JOB_DIRECTORY = Arrays.asList("jobs", "configurations");

    public static List<String> getStaticJobDirectory() {
        return JOB_DIRECTORY;
    }
}
