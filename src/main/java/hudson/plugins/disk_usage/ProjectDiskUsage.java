/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import com.google.common.collect.Maps;
import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Lucie Votypkova
 */
public class ProjectDiskUsage implements Saveable{
    
    protected transient Job job;
    private Long diskUsageWithoutBuilds = 0l;
    protected Map<String,Map<String,Long>> slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
    private Set<DiskUsageBuildInformation> buildDiskUsage = new CopyOnWriteArraySet<DiskUsageBuildInformation>();
    private Map<String,Long> notLoadedBuilds = new ConcurrentHashMap<String, Long>();
    private boolean allBuildsLoaded;
   
    private Map<String,Long> cachedBuildDiskUsage = new HashMap<String,Long>();
    private Long cachedDiskUsageWithoutBuilds = 0L;
    private Long cachedDiskUsageWorkspace = 0L;
    private Long cachedDiskUsageNonSlaveWorkspace = 0L;
    
    public ProjectDiskUsage(){
        
    }
    
    
    public void removeNode(Node node){
        slaveWorkspacesUsage.remove(node.getDisplayName());
    }
    
    public Map<String,Map<String,Long>> getSlaveWorkspacesUsage(){
        return Maps.newHashMap(slaveWorkspacesUsage);
    }
    
    public Long getDiskUsageWithoutBuilds(){
        if(diskUsageWithoutBuilds == null){
            //in older versions
            diskUsageWithoutBuilds = 0L;
        }
        return diskUsageWithoutBuilds + getDiskUsageUnloadedBuilds();
    }
    
    public Long getBuildLoadedBuildDiskUsage(){
        Long size = 0l;
        for(DiskUsageBuildInformation info : buildDiskUsage){
            size += info.getSize();
        }
        return size;
    }
    
    public Long getDiskUsageWithoutBuildDirectory(){
        return diskUsageWithoutBuilds;
    }
    
    public void setDiskUsageWithoutBuilds(Long diskUsage){
        this.diskUsageWithoutBuilds = diskUsage;
        save();
    }
    
    public Long getDiskUsageUnloadedBuilds(){
        Long size = 0L;
        for(Long s: notLoadedBuilds.values()){
            size += s;
        }
        return size;
    }

    /**
     * Size of all builds - loaded and not loaded. It should be equals to the size of directories in builds directory
     *
     * @return XXX
     */
    public int getCountOfAllBuilds(){
        return notLoadedBuilds.size() + buildDiskUsage.size();
    }
    
    public Set<String> getNotLoadedBuilds(){
        Set<String> set = new HashSet<String>();
        set.addAll(notLoadedBuilds.keySet());
        return set;
    }
    
    public String getPath(String buildDirName){
        File file = new File(job.getBuildDir(), buildDirName);
        return file.getAbsolutePath();
    }
    
    public boolean isBuildXmlExists(String buildDirName){
        File file = new File(new File(job.getBuildDir(), buildDirName),"build.xml");
        return file.exists();
    }
    
    public void addNotLoadedBuild(File file, Long size){
        if(file.equals(new File(job.getBuildDir(),"legacyIds"))){
            //it is not build.
            return;
        }
         notLoadedBuilds.put(file.getName(), size);
         DiskUsageBuildInformation information = getDiskUsageBuildInformation(file.getName());
         if(information!=null){
            buildDiskUsage.remove(information); 
         }
         save();
    }
    
    public int countLoadedBuilds(){
        return buildDiskUsage.size();
    }
    
    public void moveToUnloadedBuilds(DiskUsageBuildInformation information){
        buildDiskUsage.remove(information);
        notLoadedBuilds.put(information.getId(), information.getSize());
        save();
    }
    
    public List<File> getDirectoriesOfUnloadableBuilds(){
        List<File> files = new ArrayList<File>();
        for(String name : notLoadedBuilds.keySet()){
            files.add(new File(job.getBuildDir(),name));
        }
        return files;
    }
    
    public void moveToLoadedBuilds(AbstractBuild build, Long size){
       DiskUsageBuildInformation information =  new DiskUsageBuildInformation(build.getId(), build.getTimeInMillis(), build.getNumber(), size, build.isKeepLog());
       notLoadedBuilds.remove(build.getId());
       //id can be both (number or time creation) in case old builds
       notLoadedBuilds.remove(build.getNumber());
       addBuildInformation(information, build, size);
       save();
    }
    
    public Long getSizeOfNotLoadedBuild(String directoryName){
        return notLoadedBuilds.get(directoryName);
    }
    
    public void removeDeletedNotLoadedBuild(File file){
        notLoadedBuilds.remove(file.getName());
        save();
    }
    
    private boolean containsDiskUsageBuildInformationForDirectory(File file){
        for(DiskUsageBuildInformation info : buildDiskUsage){
            if(info.getId().equals(file.getName()) || info.getOldId().equals(file.getName())){
                return true;
            }
        }
        return false;
    }
    
    public boolean containDataForBuildDirectory(File file){
        String name = file.getName();
        return (notLoadedBuilds.get(name)!= null || containsDiskUsageBuildInformationForDirectory(file));
    }
    
    public void removeDeletedNotLoadedBuild(String fileName){
        notLoadedBuilds.remove(fileName);
        save();
    }
            
     public XmlFile getConfigFile(){
        return new XmlFile(new File(job.getRootDir(), "disk-usage.xml"));
    }
     
     public void setProject(Job job){
         this.job = job;
     }
     
     public boolean isBuildsLoaded(){
         return buildDiskUsage!=null;
     }
     
     public Set<DiskUsageBuildInformation> getBuildDiskUsage(boolean needAll){
         if(needAll){
             try{
                ProjectBuildChecker.checkValidityOfBuildData(this);
             }
             catch(Exception e){
                 Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to load builds "+getConfigFile(),e);
             }
         }
         Set<DiskUsageBuildInformation> information = new HashSet<DiskUsageBuildInformation>();
         information.addAll(buildDiskUsage);
         return information;
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
    
    public void removeBuild(DiskUsageBuildInformation information){
        buildDiskUsage.remove(information);
        save();
    }
    
    private int numberOfBuildFolders() throws IOException{
        File file = job.getBuildDir();
        int count = 0;
        if(file!=null && file.exists() && file.isDirectory()){
            for(File f : file.listFiles()){
                //file.exists() is called because symlinks to not existed files are not considered as symlinks
                if(!FileUtils.isSymlink(f) && f.exists()){
                    count++;
                }
            }
        }
        return count;
    }
    
    public void putSlaveWorkspaceSize(Node node, String path, Long size){
        Map<String,Long> workspacesInfo = slaveWorkspacesUsage.get(node.getNodeName());
        if(workspacesInfo==null)
            workspacesInfo = new ConcurrentHashMap<String,Long>();
        //worksace with 0 are only initiative (are not counted yet) or does not exists
        //no nexist workspaces are removed in method checkWorkspaces in class DiskUsageProperty
        if(workspacesInfo.get(path)==null || size>0l ){ 
            workspacesInfo.put(path, size);
        }
        slaveWorkspacesUsage.put(node.getNodeName(), workspacesInfo);
    }
    
    public Map<String,Long> getUnusedBuilds(){
        Map<String,Long> unusedBuilds = new HashMap<String,Long>();
        unusedBuilds.putAll(notLoadedBuilds);
        return unusedBuilds;
    }
    
    public Long getSizeOfNotLoadedBuilds(){
        Long size = 0L;
        for(Long s : notLoadedBuilds.values()){
            size += s;
        }
        return size;
    }
    
    
    
    public boolean containsBuildWithId(String id){
        for(DiskUsageBuildInformation inf : buildDiskUsage){
            if(inf.getId().equals(id) || inf.getOldId().equals(id)){
                return true;
            }
        }
        return false;
    }
    
    public List<File> getFilesOfNotLoadedBuilds(){
        List<File> files = new ArrayList<File>();
        for(String dirName : notLoadedBuilds.keySet()){
            files.add(new File(job.getBuildDir(),dirName));
        }
        return files;
    }
    
    public boolean allBuildsExists(){
        boolean exist = true;
        for(DiskUsageBuildInformation info : getBuildDiskUsage(false)){
            File file = new File(job.getBuildDir(),info.getId());
            if(!file.exists()){
                removeBuild(info);
                exist = false;
            }
        }
        return exist;
        
    }
    
    public void loadAllBuilds() throws IOException{
        loadAllBuilds(false);
    }
    
    public void loadAllBuilds(boolean complete) throws IOException{
        load();
        int loadedBuildInformation = getCountOfAllBuilds();
        if(loadedBuildInformation==numberOfBuildFolders() && allBuildsExists()){
            return;
        }
        if(complete && job instanceof AbstractProject){  
            ProjectBuildChecker.valideBuildData((AbstractProject)job);
            allBuildsLoaded = true;
        }
        else{
            ProjectBuildChecker.checkValidityOfBuildData(this);
        }
        DiskUsageProperty property = DiskUsageUtil.getDiskUsageProperty(job);
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
                if(!(buildDiskUsage instanceof CopyOnWriteArraySet)){
                    //saved collection is not serialized in previous versions.
                    Set<DiskUsageBuildInformation> informations = new CopyOnWriteArraySet<DiskUsageBuildInformation>();
                    informations.addAll(buildDiskUsage);
                    buildDiskUsage = informations;
                }
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
        buildDiskUsage = new CopyOnWriteArraySet<DiskUsageBuildInformation>();
        List<Run> list = job.getBuilds();
        for(Run run : list){
            if(run instanceof AbstractBuild){
                AbstractBuild build = (AbstractBuild) run;
                BuildDiskUsageAction usage = run.getAction(BuildDiskUsageAction.class);
                DiskUsageBuildInformation information = new DiskUsageBuildInformation(build.getId(), build.getTimeInMillis(), build.number, 0l, build.isKeepLog());
                 addBuildInformation(information , build);
                if(usage!=null){
                    information.setSize(usage.buildDiskUsage);
                    run.getAllActions().remove(usage);
                }
            }
        }
        save();
    }
    
    public DiskUsageBuildInformation getDiskUsageBuildInformation(int number){
        for(DiskUsageBuildInformation information : buildDiskUsage){
            if(information.getNumber()==number){
                return information;
            }
        }
        return null;
    }
    
    public DiskUsageBuildInformation getDiskUsageBuildInformation(String id){
        for(DiskUsageBuildInformation information : buildDiskUsage){
            if(information.getId().equals(id)){
                return information;
            }
        }
        return null;
    }

    public void addNotExactBuildInformation(DiskUsageBuildInformation info){
        if(!containsBuildWithId(info.getId()) || !containsBuildWithId(info.getOldId())){
            buildDiskUsage.add(info);
        }
        if(notLoadedBuilds.containsKey(info.getId()) || notLoadedBuilds.containsKey(info.getOldId())){
            notLoadedBuilds.remove(info.getId());
        }
        save();
    }

    public void addBuildInformation(Run run, long size){

    }
    
    public void addBuild(AbstractBuild build){
        DiskUsageBuildInformation information =  new DiskUsageBuildInformation(build.getId(), build.getTimeInMillis(), build.getNumber(), 0L, build.isKeepLog());
        addBuildInformation(information, build);

    }
    
    public void addBuildInformation(DiskUsageBuildInformation info, AbstractBuild build){
        addBuildInformation(info, build, 0L);
    }

    public void addBuildInformation(Run run, Long size){
        DiskUsageBuildInformation information = null;
        if(!containsBuildWithId(run.getId())){
            information = new DiskUsageBuildInformation(run.getId(), run.getStartTimeInMillis(), run.getNumber(), size);
            buildDiskUsage.add(information);
        }
        else{
            information = getDiskUsageBuildInformation(run.getId());
            information.setSize(size);
        }
        if(notLoadedBuilds.containsKey(information.getId()) || notLoadedBuilds.containsKey(information.getOldId())){
            notLoadedBuilds.remove(information.getId());
        }
        save();
    }
       
    public void addBuildInformation(DiskUsageBuildInformation info, AbstractBuild build, Long size){
        if(!containsBuildWithId(info.getId())){
                buildDiskUsage.add(info);
            if(build!=null && build.getWorkspace()!=null){
                boolean exists = false;
                try {
                     exists = build.getWorkspace().exists();
                    }
                catch(Exception ex){
                    Logger.getLogger(getClass().getName()).log(Level.FINEST, ex.getMessage(), ex);
                }
                if(exists) {
                    putSlaveWorkspaceSize(build.getBuiltOn(), build.getWorkspace().getRemote(), size);
                }
            }
        }
        if(notLoadedBuilds.containsKey(info.getId()) || notLoadedBuilds.containsKey(info.getOldId())){
            notLoadedBuilds.remove(info.getId());
        }
        save();
    }

    /**
     * @return the cachedBuildDiskUsage
     */
    public Map<String,Long> getCachedBuildDiskUsage() {
        return cachedBuildDiskUsage;
    }

    /**
     * @param cachedBuildDiskUsage the cachedBuildDiskUsage to set
     */
    public void setCachedBuildDiskUsage(Map<String,Long> cachedBuildDiskUsage) {
        this.cachedBuildDiskUsage = cachedBuildDiskUsage;
    }

    /**
     * @return the cachedDiskUsageWithoutBuilds
     */
    public Long getCachedDiskUsageWithoutBuilds() {
        return cachedDiskUsageWithoutBuilds;
    }

    /**
     * @param cachedDiskUsageWithoutBuilds the cachedDiskUsageWithoutBuilds to set
     */
    public void setCachedDiskUsageWithoutBuilds(Long cachedDiskUsageWithoutBuilds) {
        this.cachedDiskUsageWithoutBuilds = cachedDiskUsageWithoutBuilds;
    }

    /**
     * @return the cachedDiskUsageWorkspace
     */
    public Long getCachedDiskUsageWorkspace() {
        return cachedDiskUsageWorkspace;
    }

    /**
     * @param cachedDiskUsageWorkspace the cachedDiskUsageWorkspace to set
     */
    public void setCachedDiskUsageWorkspace(Long cachedDiskUsageWorkspace) {
        this.cachedDiskUsageWorkspace = cachedDiskUsageWorkspace;
    }

    /**
     * @return the cachedDiskUsageNonSlaveWorkspace
     */
    public Long getCachedDiskUsageNonSlaveWorkspace() {
        return cachedDiskUsageNonSlaveWorkspace;
    }

    /**
     * @param cachedDiskUsageNonSlaveWorkspace the cachedDiskUsageNonSlaveWorkspace to set
     */
    public void setCachedDiskUsageNonSlaveWorkspace(Long cachedDiskUsageNonSlaveWorkspace) {
        this.cachedDiskUsageNonSlaveWorkspace = cachedDiskUsageNonSlaveWorkspace;
    }
    
}
