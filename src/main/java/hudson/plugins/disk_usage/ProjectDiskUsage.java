/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import com.google.common.collect.Maps;
import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 *
 * @author Lucie Votypkova
 */
public class ProjectDiskUsage implements Saveable{
    
    protected transient Job job;
    protected Long diskUsageWithoutBuilds = 0l;
    protected Map<String,Map<String,Long>> slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
    private Set<DiskUsageBuildInformation> buildDiskUsage;
   
    
    public Map<String,Map<String,Long>> getSlaveWorkspacesUsage(){
        return Maps.newHashMap(slaveWorkspacesUsage);
    }
            
     public XmlFile getConfigFile(){
        return new XmlFile(new File(job.getRootDir(), "disk-usage.xml"));
    }
     
     public void setProject(Job job){
         this.job = job;
     }
     
     public Set<DiskUsageBuildInformation> getBuildDiskUsage(){
         if(buildDiskUsage==null){
             try{
                loadFirstTime();
             }
             catch(Exception e){
                 return new HashSet<DiskUsageBuildInformation>();
             }
         }
         return buildDiskUsage;
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
    
    public synchronized void loadFirstTime(){
        buildDiskUsage = new HashSet<DiskUsageBuildInformation>();
        List<Run> list = job.getBuilds();
        for(Run run : list){
            if(run instanceof AbstractBuild){
                AbstractBuild build = (AbstractBuild) run;
                BuildDiskUsageAction usage = run.getAction(BuildDiskUsageAction.class);
                DiskUsageBuildInformation information = new DiskUsageBuildInformation(build.getId(), build.number, 0l);
                buildDiskUsage.add(information);
                if(usage!=null){
                    information.setSize(usage.buildDiskUsage);
                    run.getActions().remove(usage);
                }
            }
        }
        DiskUsageProperty property = (DiskUsageProperty) job.getProperty(DiskUsageProperty.class);
        property.checkWorkspaces(true);
        save();
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
//        if(buildDiskUsage==null){
//             //seems like it needs load old data
//             loadOldData();
//        }
//        removeDeletedBuilds();
    }
    
    /**
     * IT is only for backward compatibility to load old data. It breaks lazy loading. 
     * Should be used only one times - updating of plugin
     * 
     * @deprecated
     * 
     */
    public void loadOldData(){
        buildDiskUsage = new HashSet<DiskUsageBuildInformation>();
        List<Run> list = job.getBuilds();
        for(Run run : list){
            if(run instanceof AbstractBuild){
                AbstractBuild build = (AbstractBuild) run;
                BuildDiskUsageAction usage = run.getAction(BuildDiskUsageAction.class);
                DiskUsageBuildInformation information = new DiskUsageBuildInformation(build.getId(), build.number, 0l);
                buildDiskUsage.add(information);
                if(usage!=null){
                    information.setSize(usage.buildDiskUsage);
                    run.getActions().remove(usage);
                }
            }
        }
        save();
    }   
    
    private void removeDeletedBuilds(){
        Iterator<DiskUsageBuildInformation> iterator= buildDiskUsage.iterator();
        while(iterator.hasNext()){
            DiskUsageBuildInformation information = iterator.next();
            File buildDir = new File(Jenkins.getInstance().getBuildDirFor(job), information.getId());
            if(!buildDir.exists())
                buildDiskUsage.remove(information);
        }
    }
}
