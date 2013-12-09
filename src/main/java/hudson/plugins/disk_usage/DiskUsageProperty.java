package hudson.plugins.disk_usage;


import hudson.model.*;
import hudson.Extension;
import hudson.FilePath;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * This Property sets DiskUsage action. 
 * 
 * @author dvrzalik
 */
public class DiskUsageProperty extends JobProperty<Job<?, ?>> {

    @Override
    public Collection<? extends Action> getJobActions(Job<?, ?> job) {
        return Collections.emptyList();
    }
    
     private transient ProjectDiskUsage diskUsage = new ProjectDiskUsage();
     @Deprecated
     private Long diskUsageWithoutBuilds;
     @Deprecated
     private Map<String,Map<String,Long>> slaveWorkspacesUsage;
                
     public void setDiskUsageWithoutBuilds(Long diskUsageWithoutBuilds){
            if(diskUsageWithoutBuilds==null)
                return;
            this.diskUsage.diskUsageWithoutBuilds = diskUsageWithoutBuilds;
            saveDiskUsage();
        }
     
     
     public void remove(Node node, String path){
          Map<String,Long> workspacesInfo = diskUsage.slaveWorkspacesUsage.get(node.getNodeName());
          workspacesInfo.remove(path);
          if(workspacesInfo.isEmpty()){
              diskUsage.slaveWorkspacesUsage.remove(node.getNodeName());
          }
          saveDiskUsage();
     }
     
    @Override
     public void setOwner(Job job){
         super.setOwner(job);
         diskUsage = new ProjectDiskUsage();
         diskUsage.setProject(job);
         loadDiskUsage();
         //transfer old data
         boolean modified = false;
         if(diskUsageWithoutBuilds!=null){
             diskUsage.diskUsageWithoutBuilds = diskUsageWithoutBuilds;
             diskUsageWithoutBuilds = null;
             modified = true;
         }
         if(slaveWorkspacesUsage!=null){
             diskUsage.slaveWorkspacesUsage.putAll(slaveWorkspacesUsage);
             slaveWorkspacesUsage = null;
             modified = true;
         }
         if(modified)
             saveDiskUsage();
     }
        
    public void putSlaveWorkspace(Node node, String path){
        Map<String,Long> workspacesInfo = diskUsage.slaveWorkspacesUsage.get(node.getNodeName());
        if(workspacesInfo==null){
           workspacesInfo = new ConcurrentHashMap<String,Long>();
        }
        if(!workspacesInfo.containsKey(path))
            workspacesInfo.put(path, 0l);
        diskUsage.slaveWorkspacesUsage.put(node.getNodeName(), workspacesInfo);
        saveDiskUsage();
    }

    public Map<String,Map<String,Long>> getSlaveWorkspaceUsage(){
        if(diskUsage.slaveWorkspacesUsage==null)
           diskUsage.slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
        return diskUsage.slaveWorkspacesUsage;
    }

    public void putSlaveWorkspaceSize(Node node, String path, Long size){
        if(diskUsage.slaveWorkspacesUsage==null)
           diskUsage.slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
        Map<String,Long> workspacesInfo = diskUsage.slaveWorkspacesUsage.get(node.getNodeName());
        if(workspacesInfo==null)
            workspacesInfo = new ConcurrentHashMap<String,Long>();
        workspacesInfo.put(path, size);
        diskUsage.slaveWorkspacesUsage.put(node.getNodeName(), workspacesInfo);
        saveDiskUsage();
    }

    public Long getWorkspaceSize(Boolean containdedInWorkspace){
        Long size = 0l;
        if(diskUsage.slaveWorkspacesUsage==null)
           diskUsage.slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
        for(String nodeName: diskUsage.slaveWorkspacesUsage.keySet()){
            Node node = Jenkins.getInstance().getNode(nodeName);
            String workspacePath = null;
            if(node instanceof Jenkins){
                workspacePath = Jenkins.getInstance().getRawWorkspaceDir();
            }
            if(node instanceof Slave){
                workspacePath = ((Slave) node).getRemoteFS();
            }
            if(workspacePath==null)
                continue;
            Map<String,Long> paths = diskUsage.slaveWorkspacesUsage.get(nodeName);
            for(String path: paths.keySet()){
                if(containdedInWorkspace.equals(path.startsWith(workspacePath))){
                    size += paths.get(path);
                }
            }
        }
        return size;
    }

    public void checkWorkspaces() {
            if(diskUsage.slaveWorkspacesUsage==null)
                diskUsage.slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
            List<AbstractBuild> builds = (List<AbstractBuild>) owner.getBuilds();
                for(AbstractBuild build: builds){
                    if(!build.isBuilding()){
                        Node node = build.getBuiltOn();
                        FilePath path = build.getWorkspace();
                        if(path==null)
                            continue;
                        putSlaveWorkspace(node, path.getRemote());
                    }
                }
            //only if it is wanted - can cost a quite long time to do it for all
            if(Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().getCheckWorkspaceOnSlave() && owner instanceof TopLevelItem){
                for(Node node: Jenkins.getInstance().getNodes()){
                    if(node.toComputer()!=null && node.toComputer().isOnline()){
                        FilePath path =null;
                        try{
                            path = node.getWorkspaceFor((TopLevelItem)owner);                  
                            if(path!=null && path.exists() && (diskUsage.slaveWorkspacesUsage.get(node.getNodeName())==null || !diskUsage.slaveWorkspacesUsage.get(node.getNodeName()).containsKey(path.getRemote()))){
                                putSlaveWorkspace(node, path.getRemote());
                            }
                        }
                        catch(Exception e){
                            LOGGER.warning("Can not check if file " + path.getRemote() + " exists on node " + node.getNodeName());
                        }
                    }
                }
            }
            //delete name of slaves which do not exist
            Iterator<String> iterator = diskUsage.slaveWorkspacesUsage.keySet().iterator();
            while(iterator.hasNext()){
                String nodeName = iterator.next();
                if(Jenkins.getInstance().getNode(nodeName)==null && !nodeName.isEmpty())//Jenkins master has empty name
                    diskUsage.slaveWorkspacesUsage.remove(nodeName);
            }
            saveDiskUsage();
    }
    
    public Long getAllNonSlaveOrCustomWorkspaceSize(){
        if(diskUsage.slaveWorkspacesUsage==null)
           diskUsage.slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
        Long size = 0l;
        for(String nodeName: diskUsage.slaveWorkspacesUsage.keySet()){
            Node node = null;  
            if(nodeName.isEmpty()){
                node = Jenkins.getInstance();
            }
            else{
                node = Jenkins.getInstance().getNode(nodeName);
            }            
            if(node==null) //slave does not exist
                continue;
            Map<String,Long> paths = diskUsage.slaveWorkspacesUsage.get(nodeName);
            for(String path: paths.keySet()){
                TopLevelItem item = null;
                if(owner instanceof TopLevelItem){
                    item = (TopLevelItem) owner;
                }
                else{
                    item = (TopLevelItem) owner.getParent();
                }
                System.out.println("path " + path + " node " + node.getDisplayName());
                try{
                    if(!isContainedInWorkspace(item, node, path)){      
                        size += paths.get(path);
                        System.out.println(size);
                    }
                }
                catch(Exception e){
                    LOGGER.log(Level.WARNING, "Can not get workspace for " + item.getDisplayName() + " on " + node.getDisplayName(), e);
                }
            }
        }
        return size;
    }
    
    private boolean isContainedInWorkspace(TopLevelItem item, Node node, String path){
        if(node instanceof Slave){
            Slave slave = (Slave) node;
            return path.contains(slave.getRemoteFS());
        }
        else{
            if(node instanceof Jenkins){
               FilePath file = Jenkins.getInstance().getWorkspaceFor(item);
               return path.contains(file.getRemote());
            }
            else{
                try{
                    return path.contains(node.getWorkspaceFor(item).getRemote());
                }
                catch(Exception e){
                    return false;
                }
            }
        }
    }

    public Long getAllWorkspaceSize(){
        if(diskUsage.slaveWorkspacesUsage==null)
           diskUsage.slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
        Long size = 0l;
        for(String nodeName: diskUsage.slaveWorkspacesUsage.keySet()){
            Node slave = Jenkins.getInstance().getNode(nodeName);
            if(slave==null && !nodeName.isEmpty()) //slave does not exist
                continue;
            Map<String,Long> paths = diskUsage.slaveWorkspacesUsage.get(nodeName);
            for(String path: paths.keySet()){
                    size += paths.get(path);
            }
        }
        return size;
    }

//    public Object readResolve() {
//        diskUsage = new ProjectDiskUsage();      
//         if(diskUsage.diskUsageWithoutBuilds == null)
//             diskUsage.diskUsageWithoutBuilds = 0l;
//         if(diskUsage.slaveWorkspacesUsage==null)
//            diskUsage.slaveWorkspacesUsage = new ConcurrentHashMap<String,Map<String,Long>>();
//         return this;
//     }

    public Long getDiskUsageWithoutBuilds(){
        if(diskUsage.diskUsageWithoutBuilds==null)
            diskUsage.diskUsageWithoutBuilds=0l;
        return diskUsage.diskUsageWithoutBuilds;
    }

    public Long getAllDiskUsageWithoutBuilds(){
        if(diskUsage.diskUsageWithoutBuilds==null)
            diskUsage.diskUsageWithoutBuilds=0l;
       Long usage = diskUsage.diskUsageWithoutBuilds;
       if(owner instanceof ItemGroup){
                 ItemGroup group = (ItemGroup) owner;
                     usage += getDiskUsageWithoutBuildsAllSubItems(group);
       }
       return usage;
    }

    private Long getDiskUsageWithoutBuildsAllSubItems(ItemGroup group){
        Long usage = 0l;
        for(Object item: group.getItems()){
            if(item instanceof ItemGroup){
               ItemGroup subGroup = (ItemGroup) item;
               usage += getDiskUsageWithoutBuildsAllSubItems(subGroup);
            }
            if(item instanceof AbstractProject){
                AbstractProject p = (AbstractProject) item;
                DiskUsageProperty property = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                if(property!=null){
                    usage += property.getDiskUsageWithoutBuilds();
                }
            }
        }
        return usage;
    }

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void transitionAuth() throws IOException {
        DiskUsageDescriptor that = (DiskUsageDescriptor) Hudson.getInstance().getDescriptor(DiskUsageProperty.class);
        if(that == null){
            LOGGER.warning("Cannot convert DiskUsageProjectActions, DiskUsageDescripto is null, check log for previous DI error, e.g. Guice errors.");
            return;
        }
        if (!that.converted) {
            DiskUsageProjectActionFactory.DESCRIPTOR.setShowGraph(that.showGraph);
            that.converted = true;
            that.save();
            DiskUsageProjectActionFactory.DESCRIPTOR.save();
        }
    }    

    public synchronized void saveDiskUsage() {
        diskUsage.save();
    }
    
    public synchronized void loadDiskUsage(){
        diskUsage.load();        
    }

    @Extension
    public static final class DiskUsageDescriptor extends JobPropertyDescriptor {

        @Deprecated
        private boolean showGraph;

        @Deprecated
        private boolean converted;

        public DiskUsageDescriptor() {
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }

        public boolean showGraph(){
            return showGraph;
        }


        @Override
        public DiskUsageProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
             return new DiskUsageProperty();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            return true;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }
    }
    
    public static final Logger LOGGER = Logger.getLogger(DiskUsageProperty.class.getName());
}

    
