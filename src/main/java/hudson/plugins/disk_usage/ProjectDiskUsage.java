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
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Node;
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
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Lucie Votypkova
 */
public class ProjectDiskUsage implements Saveable {

    protected transient Job job;
    protected Long diskUsageWithoutBuilds = 0l;
    protected Map<String, Map<String, Long>> slaveWorkspacesUsage = new ConcurrentHashMap<String, Map<String, Long>>();
    private Set<DiskUsageBuildInformation> buildDiskUsage = new CopyOnWriteArraySet<DiskUsageBuildInformation>();
    private boolean allBuildsLoaded;


    public Map<String, Map<String, Long>> getSlaveWorkspacesUsage() {
        return Maps.newHashMap(slaveWorkspacesUsage);
    }

    public XmlFile getConfigFile() {
        return new XmlFile(new File(job.getRootDir(), "disk-usage.xml"));
    }

    public void setProject(Job job) {
        this.job = job;
    }

    public boolean isBuildsLoaded() {
        return buildDiskUsage != null;
    }

    public Set<DiskUsageBuildInformation> getBuildDiskUsage(boolean needAll) {
        Set<DiskUsageBuildInformation> information = new HashSet<DiskUsageBuildInformation>();
        AbstractProject p = (AbstractProject) job;
        if(needAll && !allBuildsLoaded) {
            try {
                loadAllBuilds();
            }
            catch (Exception e) {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to load builds " + getConfigFile(), e);
            }
        }
        information.addAll(buildDiskUsage);
        return information;
    }

    public synchronized void save() {
        if(BulkChange.contains(this)) {
            return;
        }
        try {
            getConfigFile().write(this);
            SaveableListener.fireOnChange(this, getConfigFile());
        } catch (IOException e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to save " + getConfigFile(), e);
        }

    }

    public void removeBuild(DiskUsageBuildInformation information) {
        buildDiskUsage.remove(information);
    }

    private int numberOfBuildFolders() throws IOException {
        File file = job.getBuildDir();
        int count = 0;
        if(file != null && file.exists() && file.isDirectory()) {
            for(File f: file.listFiles()) {
                if(!FileUtils.isSymlink(f) && !f.isDirectory()) {
                    count++;
                }
            }
        }
        return count;
    }

    public void putSlaveWorkspaceSize(Node node, String path, Long size) {
        Map<String, Long> workspacesInfo = slaveWorkspacesUsage.get(node.getNodeName());
        if(workspacesInfo == null) {
            workspacesInfo = new ConcurrentHashMap<String, Long>();
        }
        //worksace with 0 are only initiative (are not counted yet) or does not exists
        //no nexist workspaces are removed in method checkWorkspaces in class DiskUsageProperty
        if(workspacesInfo.get(path) == null || size > 0l) {
            workspacesInfo.put(path, size);
        }
        slaveWorkspacesUsage.put(node.getNodeName(), workspacesInfo);
    }

    public boolean containsBuildWithId(String id) {
        for(DiskUsageBuildInformation inf: buildDiskUsage) {
            if(inf.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public void loadAllBuilds() throws IOException {
        load();
        int loadedBuildInformation = buildDiskUsage.size();
        if(loadedBuildInformation >= numberOfBuildFolders()) {
            return;
        }
        AbstractProject project = (AbstractProject) job;
        List<Run> list = project.getBuilds();
        buildDiskUsage = new CopyOnWriteArraySet<DiskUsageBuildInformation>();
        for(Run run: list) {
            if(run instanceof AbstractBuild) {
                if(containsBuildWithId(run.getId())) {
                    continue;
                }
                AbstractBuild build = (AbstractBuild) run;
                BuildDiskUsageAction toRemove = null;
                long buildOldDiskUsage = 0l;
                //if not present, add
                for(Action action: build.getActions()) {
                    if(action instanceof BuildDiskUsageAction) {
                        toRemove = (BuildDiskUsageAction) action;
                        buildOldDiskUsage = toRemove.buildDiskUsage;
                    }
                }
                if(toRemove != null) {
                    build.getActions().remove(toRemove);
                }
                if(build.getWorkspace() != null) {
                    putSlaveWorkspaceSize(build.getBuiltOn(), build.getWorkspace().getRemote(), 0l);
                }
                DiskUsageBuildInformation information = new DiskUsageBuildInformation(build.getId(), build.getTimeInMillis(), build.number, 0l);
                addBuildInformation(information, build);
                if(information.getSize() == 0l) {
                    information.setSize(buildOldDiskUsage);
                }
//                if(usage==null){
//                    run.getActions().add(new BuildDiskUsageAction(build));
//                    try {
//                        build.save();
//                    } catch (IOException ex) {
//                        Logger.getLogger(ProjectDiskUsage.class.getName()).log(Level.SEVERE, null, ex);
//                    }
//                }
                // else{
                //  }
                //DiskUsageUtil.addBuildDiskUsageAction(build);
            }
        }
        allBuildsLoaded = true;
        DiskUsageProperty property = (DiskUsageProperty) job.getProperty(DiskUsageProperty.class);
        property.checkWorkspaces(true);
        save();
    }

    public synchronized void load() {
        XmlFile file = getConfigFile();
        if(!file.getFile().exists()) {
            return;
        }
        try {
            file.unmarshal(this);
            if(buildDiskUsage instanceof HashSet) {
                //saved collection is not serialized in previous versions.
                Set<DiskUsageBuildInformation> informations = new CopyOnWriteArraySet<DiskUsageBuildInformation>();
                informations.addAll(buildDiskUsage);
                buildDiskUsage = informations;
            }
        } catch (IOException e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to load " + file, e);
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
    public void loadOldData() {
        buildDiskUsage = new CopyOnWriteArraySet<DiskUsageBuildInformation>();
        List<Run> list = job.getBuilds();
        for(Run run: list) {
            if(run instanceof AbstractBuild) {
                AbstractBuild build = (AbstractBuild) run;
                BuildDiskUsageAction usage = run.getAction(BuildDiskUsageAction.class);
                DiskUsageBuildInformation information = new DiskUsageBuildInformation(build.getId(), build.getTimeInMillis(), build.number, 0l);
                addBuildInformation(information, build);
                if(usage != null) {
                    information.setSize(usage.buildDiskUsage);
                    run.getAllActions().remove(usage);
                }
            }
        }
        save();
    }

    public DiskUsageBuildInformation getDiskUsageBuildInformation(int number) {
        for(DiskUsageBuildInformation information: buildDiskUsage) {
            if(information.getNumber() == number) {
                return information;
            }
        }
        return null;
    }

    public void addBuildInformation(DiskUsageBuildInformation info, AbstractBuild build) {
        if(!containsBuildWithId(info.getId())) {
            buildDiskUsage.add(info);
            if(build != null && build.getWorkspace() != null) {
                putSlaveWorkspaceSize(build.getBuiltOn(), build.getWorkspace().getRemote(), 0l);
            }
        }
    }

    private void removeDeletedBuilds() {

        Iterator<DiskUsageBuildInformation> iterator = buildDiskUsage.iterator();
        while(iterator.hasNext()) {
            DiskUsageBuildInformation information = iterator.next();
            File buildDir = new File(Jenkins.getInstance().getBuildDirFor(job), information.getId());
            if(!buildDir.exists()) {
                buildDiskUsage.remove(information);
            }
        }
    }
}
