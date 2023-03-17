package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.FilePath;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TopLevelItem;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

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
    private Map<String, Map<String, Long>> slaveWorkspacesUsage;

    public void setDiskUsageWithoutBuilds(Long diskUsageWithoutBuilds) {
        if(diskUsage == null) {
            diskUsage = new ProjectDiskUsage();
        }
        diskUsage.load();
        this.diskUsage.diskUsageWithoutBuilds = diskUsageWithoutBuilds;
        saveDiskUsage();
    }


    public void remove(Node node, String path) {
        Map<String, Long> workspacesInfo = getAgentWorkspaceUsage().get(node.getNodeName());
        workspacesInfo.remove(path);
        if(workspacesInfo.isEmpty()) {
            getAgentWorkspaceUsage().remove(node.getNodeName());
        }
        saveDiskUsage();
    }

    public Set<DiskUsageBuildInformation> getDiskUsageOfBuilds() {
        return diskUsage.getBuildDiskUsage(false);
    }

    public Long getDiskUsageOfBuild(String buildId) {
        for(DiskUsageBuildInformation information: diskUsage.getBuildDiskUsage(false)) {
            if(buildId.equals(information.getId())) {
                return information.getSize();
            }
        }
        return 0L;
    }

    public DiskUsageBuildInformation getDiskUsageBuildInformation(String buildId) {
        for(DiskUsageBuildInformation information: diskUsage.getBuildDiskUsage(false)) {
            if(buildId.equals(information.getId())) {
                return information;
            }
        }
        return null;
    }

    public Long getAllDiskUsageOfBuild(String buildId) {
        return getAllDiskUsageOfBuild(getDiskUsageBuildInformation(buildId).getNumber());
    }

    public Long getAllDiskUsageOfBuild(int buildNumber) {
        Long size = getDiskUsageOfBuild(buildNumber);
        if(owner instanceof ItemGroup) {
            ItemGroup group = (ItemGroup) owner;
            for(Object item: group.getItems()) {
                if(item instanceof AbstractProject) {
                    AbstractProject project = (AbstractProject) item;
                    DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
                    size += property.getAllDiskUsageOfBuild(buildNumber);
                }
            }
        }
        return size;
    }


    public DiskUsageBuildInformation getDiskUsageBuildInformation(int buildNumber) {
        for(DiskUsageBuildInformation information: diskUsage.getBuildDiskUsage(false)) {
            if(buildNumber == information.getNumber()) {
                return information;
            }
        }
        return null;
    }

    public Long getDiskUsageOfBuild(int buildNumber) {
        for(DiskUsageBuildInformation information: diskUsage.getBuildDiskUsage(false)) {
            if(buildNumber == information.getNumber()) {
                return information.getSize();
            }
        }
        return 0L;
    }

    public ProjectDiskUsage getProjectDiskUsage() {
        return diskUsage;
    }

    public ProjectDiskUsage getDiskUsage() {
        return diskUsage;
    }

    @Override
    public void setOwner(Job job) {
        super.setOwner(job);
        diskUsage = new ProjectDiskUsage();
        diskUsage.setProject(job);
        loadDiskUsage();
        // transfer old data
        boolean modified = false;
        if(diskUsageWithoutBuilds != null) {
            diskUsage.diskUsageWithoutBuilds = diskUsageWithoutBuilds;
            diskUsageWithoutBuilds = null;
            modified = true;
        }
        if(slaveWorkspacesUsage != null) {
            diskUsage.slaveWorkspacesUsage.putAll(slaveWorkspacesUsage);
            slaveWorkspacesUsage = null;
            modified = true;
        }
        if(modified) {
            saveDiskUsage();
            try {
                job.save();
            }
            catch (Exception e) {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "configuration of project " + job.getDisplayName() + " can not be saved.", e);
            }
        }
    }

    @Deprecated(forRemoval = true)
    public void putSlaveWorkspace(Node node, String path) {
        putAgentWorkspace(node, path);
    }

    public void putAgentWorkspace(Node node, String path) {
        Map<String, Long> workspacesInfo = getAgentWorkspaceUsage().get(node.getNodeName());
        if(workspacesInfo == null) {
            workspacesInfo = new ConcurrentHashMap<>();
        }
        if(!workspacesInfo.containsKey(path)) {
            workspacesInfo.put(path, 0l);
        }
        getAgentWorkspaceUsage().put(node.getNodeName(), workspacesInfo);
        saveDiskUsage();
    }

    @Deprecated(forRemoval = true)
    public Map<String, Map<String, Long>> getSlaveWorkspaceUsage() {
        return getAgentWorkspaceUsage();
    }

    public Map<String, Map<String, Long>> getAgentWorkspaceUsage() {
        if(diskUsage.slaveWorkspacesUsage == null) {
            checkWorkspaces();
        }
        return diskUsage.slaveWorkspacesUsage;
    }

    @Deprecated(forRemoval = true)
    public void putSlaveWorkspaceSize(Node node, String path, Long size) {
        putAgentWorkspaceSize(node, path, size);
    }

    public void putAgentWorkspaceSize(Node node, String path, Long size) {
        Map<String, Long> workspacesInfo = getAgentWorkspaceUsage().get(node.getNodeName());
        if(workspacesInfo == null) {
            workspacesInfo = new ConcurrentHashMap<>();
        }
        workspacesInfo.put(path, size);
        getAgentWorkspaceUsage().put(node.getNodeName(), workspacesInfo);
        saveDiskUsage();
    }

    public Long getWorkspaceSize(Boolean containdedInWorkspace) {
        Long size = 0L;
        for(String nodeName: getAgentWorkspaceUsage().keySet()) {
            Node node = Jenkins.getInstance().getNode(nodeName);
            String workspacePath = null;
            if(node instanceof Jenkins) {
                workspacePath = Jenkins.getInstance().getRawWorkspaceDir();
            }
            if(node instanceof Slave) {
                workspacePath = ((Slave) node).getRemoteFS();
            }
            if(workspacePath == null) {
                continue;
            }
            Map<String, Long> paths = getAgentWorkspaceUsage().get(nodeName);
            for(Entry<String, Long> entry : paths.entrySet()) {
                if(containdedInWorkspace.equals(entry.getKey().startsWith(workspacePath))) {
                    size += entry.getValue();
                }
            }
        }
        return size;
    }

    private void checkAllBuilds() {
        List<AbstractBuild> builds = (List<AbstractBuild>) owner.getBuilds();
        for(AbstractBuild build: builds) {
            if(!build.isBuilding()) {
                Node node = build.getBuiltOn();
                FilePath path = build.getWorkspace();
                if(path == null) {
                    continue;
                }
                Map<String, Long> workspacesInfo = diskUsage.slaveWorkspacesUsage.get(node.getNodeName());
                if(workspacesInfo == null) {
                    workspacesInfo = new ConcurrentHashMap<>();
                    workspacesInfo.put(path.getRemote(), 0L);
                }
                else {
                    if(!workspacesInfo.keySet().contains(path.getRemote())) {
                        workspacesInfo.put(path.getRemote(), 0L);
                    }
                }
                getAgentWorkspaceUsage().put(node.getNodeName(), workspacesInfo);
            }
        }
    }

    private void checkLoadedBuilds() {
        if(owner instanceof AbstractProject) {
            AbstractProject project = (AbstractProject) owner;
            Collection<AbstractBuild> builds = project._getRuns().getLoadedBuilds().values();
            for(AbstractBuild build: builds) {
                if(!build.isBuilding()) {
                    Node node = build.getBuiltOn();
                    FilePath path = build.getWorkspace();
                    if(path == null) {
                        continue;
                    }
                    Map<String, Long> workspacesInfo = diskUsage.slaveWorkspacesUsage.get(node.getNodeName());
                    if(workspacesInfo == null) {
                        workspacesInfo = new ConcurrentHashMap<>();
                        workspacesInfo.put(path.getRemote(), 0L);
                    }
                    else {
                        if(!workspacesInfo.keySet().contains(path.getRemote())) {
                            workspacesInfo.put(path.getRemote(), 0L);
                        }
                    }
                    getAgentWorkspaceUsage().put(node.getNodeName(), workspacesInfo);
                }
            }
        }
    }

    public void checkWorkspaces() {
        checkWorkspaces(false);
    }

    public void checkWorkspaces(boolean force) {
        if(force) {
            checkAllBuilds();
        }
        else {
            checkLoadedBuilds();
        }
        // only if it is wanted - can cost a quite long time to do it for all
        if(Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().getCheckWorkspaceOnAgent() && owner instanceof TopLevelItem) {
            for(Node node: Jenkins.getInstance().getNodes()) {
                if(node.toComputer() != null && node.toComputer().isOnline()) {
                    FilePath path = null;
                    try {
                        path = node.getWorkspaceFor((TopLevelItem) owner);
                        if(path != null && path.exists() && (diskUsage.slaveWorkspacesUsage.get(node.getNodeName()) == null || !diskUsage.slaveWorkspacesUsage.get(node.getNodeName()).containsKey(path.getRemote()))) {
                            putAgentWorkspace(node, path.getRemote());
                        }
                    }
                    catch (IOException|InterruptedException e) {
                        if (path != null) {
                            LOGGER.warning("Can not check if file " + path.getRemote() + " exists on node " + node.getNodeName());
                        }
                    }
                }
            }
        }

        Iterator<String> iterator = diskUsage.slaveWorkspacesUsage.keySet().iterator();
        while(iterator.hasNext()) {
            String nodeName = iterator.next();
            Node node = Jenkins.getInstance().getNode(nodeName);
            if(node == null && nodeName.isEmpty()) {
                node = Jenkins.getInstance();
            }
            // delete name of agents which do not exist
            if(node == null) {// Jenkins master has empty name
                iterator.remove();
            }
            else {
                // delete path which does not exists
                if(node.toComputer() != null && node.getChannel() != null) {
                    Map<String, Long> workspaces = diskUsage.slaveWorkspacesUsage.get(nodeName);
                    Iterator<String> pathIterator = workspaces.keySet().iterator();
                    while(pathIterator.hasNext()) {
                        String path = pathIterator.next();
                        try {
                            FilePath workspace = node.createPath(path);
                            if(!workspace.exists()) {
                                pathIterator.remove();
                            }
                        }
                        catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Can not check if file " + path + " exists on node " + node.getNodeName());
                        }
                    }
                    if(workspaces.isEmpty()) {
                        iterator.remove();
                    }
                }
            }
        }
        saveDiskUsage();
    }

    @Deprecated(forRemoval = true)
    public Long getAllNonSlaveOrCustomWorkspaceSize() {
        return getAllNonAgentOrCustomWorkspaceSize();
    }

    public Long getAllNonAgentOrCustomWorkspaceSize() {
        Long size = 0L;
        for(String nodeName: getAgentWorkspaceUsage().keySet()) {
            Node node = null;
            if(nodeName.isEmpty()) {
                node = Jenkins.getInstance();
            }
            else {
                node = Jenkins.getInstance().getNode(nodeName);
            }
            if(node == null) { // agent does not exist
                continue;
            }
            Map<String, Long> paths = getAgentWorkspaceUsage().get(nodeName);
            for(Entry<String, Long> entry : paths.entrySet()) {
                Item item = null;
                if(owner instanceof TopLevelItem) {
                    item = owner;
                }
                else {
                    if (owner.getParent() instanceof TopLevelItem){
                        item = (TopLevelItem) owner.getParent();
                    }
                }
                try {
                    if(!isContainedInWorkspace(item, node, entry.getKey())) {
                        size += entry.getValue();
                    }
                }
                catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Can not get workspace for " + item.getDisplayName() + " on " + node.getDisplayName(), e);
                }
            }
        }
        return size;
    }

    private boolean isContainedInWorkspace(Item item, Node node, String path) {
        if(node instanceof Slave) {
            Slave agent = (Slave) node;
            return path.contains(agent.getRemoteFS());
        }
        else {
            if (item instanceof TopLevelItem){
                TopLevelItem topLevelItem = (TopLevelItem) item;
                if(node instanceof Jenkins) {
                    FilePath file = Jenkins.getInstance().getWorkspaceFor(topLevelItem);
                    if (file != null){
                        return path.contains(file.getRemote());
                    }
                }
                else {
                    try {
                        final var file = node.getWorkspaceFor(topLevelItem);
                        if (file != null){
                            return path.contains(file.getRemote());
                        }
                    }
                    catch (Exception e) {
                        return false;
                    }
                }
            }
            return false;
        }
    }

    public Long getAllWorkspaceSize() {
        Long size = 0L;
        for(String nodeName: getAgentWorkspaceUsage().keySet()) {
            Node agent = Jenkins.get().getNode(nodeName);
            if(agent == null && !nodeName.isEmpty()) {// agent does not exist
                continue;
            }
            Map<String, Long> paths = getAgentWorkspaceUsage().get(nodeName);
            for(Entry<String, Long> entry : paths.entrySet()) {
                size += entry.getValue();
            }
        }
        return size;
    }

    public Long getDiskUsageWithoutBuilds() {
        if(diskUsage.diskUsageWithoutBuilds == null) {
            diskUsage.diskUsageWithoutBuilds = 0l;
        }
        return diskUsage.diskUsageWithoutBuilds;
    }

    public Long getAllDiskUsageWithoutBuilds() {
        if(diskUsage.diskUsageWithoutBuilds == null) {
            diskUsage.diskUsageWithoutBuilds = 0l;
        }
        Long usage = diskUsage.diskUsageWithoutBuilds;
        if(owner instanceof ItemGroup) {
            ItemGroup group = (ItemGroup) owner;
            usage += getDiskUsageWithoutBuildsAllSubItems(group);
        }
        return usage;
    }

    private Long getDiskUsageWithoutBuildsAllSubItems(ItemGroup group) {
        Long usage = 0L;
        for(Object item: group.getItems()) {
            if(item instanceof ItemGroup) {
                ItemGroup subGroup = (ItemGroup) item;
                usage += getDiskUsageWithoutBuildsAllSubItems(subGroup);
            }
            if(item instanceof AbstractProject) {
                AbstractProject p = (AbstractProject) item;
                DiskUsageProperty property = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                if(property != null) {
                    usage += property.getDiskUsageWithoutBuilds();
                }
            }
        }
        return usage;
    }

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void transitionAuth() throws IOException {
        DiskUsageDescriptor that = (DiskUsageDescriptor) Hudson.getInstance().getDescriptor(DiskUsageProperty.class);
        if(that == null) {
            LOGGER.warning("Cannot convert DiskUsageProjectActions, DiskUsageDescripto is null, check log for previous DI error, e.g. Guice errors.");
            return;
        }
        if(!that.converted) {
            DiskUsageProjectActionFactory.DESCRIPTOR.setShowGraph(that.showGraph);
            that.converted = true;
            that.save();
            DiskUsageProjectActionFactory.DESCRIPTOR.save();
        }
    }

    public void saveDiskUsage() {
        diskUsage.save();
    }

    public void loadDiskUsage() {
        diskUsage.load();
        // ensure that build was not removed without calling listener - badly removed, or badly saved (without build.xml)
        for(DiskUsageBuildInformation information: diskUsage.getBuildDiskUsage(false)) {
            File buildsDirectory = new File(owner.getRootDir(), "builds");
            File build = new File(buildsDirectory, information.getId());
            if(!build.exists()) {
                diskUsage.removeBuild(information);
            }
        }
        diskUsage.save();
    }

    @Override
    public JobProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return new DiskUsageProperty();
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
            return Messages.displayName();
        }

        public boolean showGraph() {
            return showGraph;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }
    }

    public static final Logger LOGGER = Logger.getLogger(DiskUsageProperty.class.getName());
}
