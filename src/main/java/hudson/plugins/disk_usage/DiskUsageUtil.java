/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.tasks.Mailer;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.RoleChecker;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageUtil {

    public static void addProperty(Item item) {
        if(item instanceof AbstractProject) {
            AbstractProject project = (AbstractProject) item;
            DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
            if(property == null) {
                try {
                    property = new DiskUsageProperty();
                    project.addProperty(property);

                } catch (IOException ex) {
                    Logger.getLogger(DiskUsageItemListener.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            loadData(property, false);
        }
        if(item instanceof ItemGroup) {

            for(AbstractProject project: DiskUsageUtil.getAllProjects((ItemGroup<?>) item)) {
                DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
                if(property == null) {
                    try {
                        property = new DiskUsageProperty();
                        project.addProperty(property);
                    } catch (IOException ex) {
                        Logger.getLogger(DiskUsageItemListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                loadData(property, false);
            }
        }
    }

    protected static void loadData(DiskUsageProperty property, boolean loadAllBuilds) {
        if(loadAllBuilds) {
            try {
                property.getDiskUsage().loadAllBuilds();
            } catch (IOException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else {
            property.loadDiskUsage();
        }
    }

    public static Date getDate(String timeCount, String timeUnit) {
        if(timeUnit == null || !timeUnit.matches("\\d+") || !timeCount.matches("\\d+")) {
            return null;
        }
        int unit = Integer.decode(timeUnit);
        int count = Integer.decode(timeCount);
        return getDate(unit, count);
    }

    public static Date getDate(int unit, int count) {
        Calendar calendar = new GregorianCalendar();
        calendar.set(unit, calendar.get(unit) - count);
        return calendar.getTime();
    }

    public static String formatTimeInMilisec(long time) {
        if(time / 1000 < 1) {
            return "0 seconds";
        }
        long inMinutes = time / 60000;
        long hours = inMinutes / 60;
        String formattedTime = "";
        if(hours > 0) {
            String unit = hours > 1 ? "hours" : "hour";
            formattedTime = hours + " " + unit;
        }
        long minutes = inMinutes - hours * 60;
        if(minutes > 0) {
            String unit = minutes > 1 ? "minutes" : "minute";
            formattedTime = formattedTime + " " + minutes + " " + unit;
        }
        long seconds = (time / 1000) - minutes * 60 - hours * 60 * 60;
        if(seconds > 0) {
            String unit = minutes > 1 ? "seconds" : "second";
            formattedTime = formattedTime + " " + seconds + " " + unit;
        }
        return formattedTime;
    }

    public static void sendEmail(String subject, String message) throws MessagingException {

        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if (plugin == null) {
            return;
        }
        String address = plugin.getConfiguration().getEmailAddress();
        if(address == null || address.isEmpty()) {
            Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "e-mail address is not set for notification about exceed disk size. Please set it in global configuration.");
            return;
        }
        MimeMessage msg = new MimeMessage(Mailer.descriptor().createSession());
        msg.setSubject(subject);
        msg.setText(message, "utf-8");
        msg.setFrom(new InternetAddress(Mailer.descriptor().getAdminAddress()));
        msg.setSentDate(new Date());
        msg.setRecipient(RecipientType.TO, new InternetAddress(address));
        Transport.send(msg);
    }

    public static Long getSizeInBytes(String stringSize) {
        if(stringSize == null || "-".equals(stringSize)) {
            return 0l;
        }
        String[] values = stringSize.split(" ");
        int index = getIndex(values[1]);
        Long value = Long.decode(values[0]);
        Double size = value * (Math.pow(1024, index));
        return Math.round(size);
    }

    public static void controlAllJobsExceedSize() throws IOException {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if (plugin == null) {
            return;
        }
        plugin.refreshGlobalInformation();
        Long allJobsSize = plugin.getCashedGlobalJobsDiskUsage();
        Long exceedJobsSize = plugin.getConfiguration().getAllJobsExceedSize();
        if(allJobsSize > exceedJobsSize) {
            try {
                sendEmail("Jobs exceed size", "Jobs exceed size " + getSizeString(exceedJobsSize) + ". Their size is now " + getSizeString(allJobsSize));
            } catch (MessagingException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting build size.", ex);
            }
        }
    }

    public static void controlWorkspaceExceedSize(AbstractProject project) {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);

        if(property == null) {
            return;
        }

        Long size = property.getAllWorkspaceSize();
        if(plugin.getConfiguration().warnAboutJobWorkspaceExceedSize() && size > plugin.getConfiguration().getJobWorkspaceExceedSize()) {
            StringBuilder builder = new StringBuilder();
            builder.append("Workspaces of Job " + project.getDisplayName() + " have size " + size + ".");
            builder.append("\n");
            builder.append("List of workspaces:");
            for(String agentName : property.getAgentWorkspaceUsage().keySet()) {
                Long s = 0L;
                for(Long l:property.getAgentWorkspaceUsage().get(agentName).values()) {
                    s += l;
                }
                builder.append("\n");
                builder.append("Agent " + agentName + " has workspace of job " + project.getDisplayName() + " with size " + getSizeString(s));
            }
            try {
                sendEmail("Workspaces of Job " + project.getDisplayName() + " exceed size", builder.toString());
            } catch (MessagingException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting build size.", ex);
            }
        }
    }

    public static List<String> parseExcludedJobsFromString(String jobs) {
        List<String> list = new ArrayList<>();
        String[] jobNames = jobs.split(",");
        for(String name: jobNames) {
            name = name.trim();
            Item item = Jenkins.get().getItem(name);
            if(item != null && item instanceof AbstractProject) {
                list.add(name);
            }
        }
        return list;
    }

    public static String getSizeString(Long size) {
        if(size == null || size <= 0) {
            return "-";
        }

        int floor = (int) getScale(size);
        floor = Math.min(floor, 4);
        double base = Math.pow(1024, floor);
        String unit = getUnitString(floor);

        return Math.round(size / base) + " " + unit;
    }

    public static double getScale(long number) {
        if(number == 0) {
            return 0;
        }
        return Math.floor(Math.log(number) / Math.log(1024));
    }

    public static int getIndex(String unit) {
        int index = 0;
        if("KB".equals(unit)) {
            index = 1;
        }
        if("MB".equals(unit)) {
            index = 2;
        }
        if("GB".equals(unit)) {
            index = 3;
        }
        if("TB".equals(unit)) {
            index = 4;
        }
        return index;
    }

    @SuppressFBWarnings("SF_SWITCH_NO_DEFAULT")
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

    /**
     * Calculate disk usage of build after its execution (or as post-build step)
     * @param build
     * @param listener 
     */
    public static void calculationDiskUsageOfBuild(AbstractBuild build, TaskListener listener) {
        if(DiskUsageProjectActionFactory.DESCRIPTOR.isExcluded(build.getProject())) {
            listener.getLogger().println("This job is excluded from disk usage calculation.");
            return;
        }
        try {
            // count build.xml too
            build.save();
            DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
            listener.getLogger().println("Started calculate disk usage of build");
            Long startTimeOfBuildCalculation = System.currentTimeMillis();
            DiskUsageUtil.calculateDiskUsageForBuild(build.getId(), build.getProject());
            listener.getLogger().println("Finished Calculation of disk usage of build in " + DiskUsageUtil.formatTimeInMilisec(System.currentTimeMillis() - startTimeOfBuildCalculation));
            DiskUsageProperty property = (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
            if(property == null) {
                DiskUsageUtil.addProperty(build.getProject());
                property =  (DiskUsageProperty) build.getProject().getProperty(DiskUsageProperty.class);
            }
            FilePath workspace = build.getWorkspace();
            if(workspace != null) {
                ArrayList<FilePath> exceededFiles = new ArrayList<>();
                AbstractProject project = build.getProject();
                Node node = build.getBuiltOn();
                if (node == null) {
                    listener.getLogger().println("Node no longer available for disk usage calculation.");
                    return;
                }
                if(project instanceof ItemGroup) {
                    List<AbstractProject> projects = DiskUsageUtil.getAllProjects((ItemGroup) project);
                    for(AbstractProject p: projects) {
                        DiskUsageProperty prop = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                        if(prop == null) {
                            prop = new DiskUsageProperty();
                            p.addProperty(prop);
                        }
                        prop.checkWorkspaces();
                        Map<String, Long> paths = prop.getAgentWorkspaceUsage().get(node.getNodeName());
                        if(paths != null && !paths.isEmpty()) {
                            for(String path: paths.keySet()) {
                                exceededFiles.add(new FilePath(node.getChannel(), path));
                            }
                        }
                    }
                }
                property.checkWorkspaces();
                listener.getLogger().println("Started calculate disk usage of workspace");
                Long startTimeOfWorkspaceCalculation = System.currentTimeMillis();
                Long size = DiskUsageUtil.calculateWorkspaceDiskUsageForPath(workspace, exceededFiles);
                listener.getLogger().println("Finished Calculation of disk usage of workspace in " + DiskUsageUtil.formatTimeInMilisec(System.currentTimeMillis() - startTimeOfWorkspaceCalculation));
                property.putAgentWorkspaceSize(node, workspace.getRemote(), size);
                property.saveDiskUsage();
                DiskUsageUtil.controlWorkspaceExceedSize(project);
                property.saveDiskUsage();
            }
        }
        catch (IOException | InterruptedException ex) {
            listener.getLogger().println("Disk usage plugin fails during calculation disk usage of this build.");
            Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin fails during build calculation disk space of job " + build.getParent().getDisplayName(), ex);
        }
    }

    public static boolean isSymlink(File f) throws IOException {
        return Util.isSymlink(f);
    }

    public static Long getFileSize(File f, List<File> exceedFiles, int curDirDepth, int maxDepthDirTraverse) throws IOException {
        long size = 0;
        if(!f.exists()) {
            return size;
        }
        if(f.isDirectory() && !isSymlink(f)) {
            if (curDirDepth < maxDepthDirTraverse || maxDepthDirTraverse == -1) {
                File[] fileList = f.listFiles();
                if(fileList != null) {
                    for(File child: fileList) {
                        if(exceedFiles.contains(child)) {
                            continue;
                        } // do not count exceeded files
                        if(!isSymlink(child)) {
                            size += getFileSize(child, exceedFiles, curDirDepth + 1, maxDepthDirTraverse);
                        }
                    }
                }
            }
            else {
                LOGGER.info("Failed to list files in " + f.getPath() + " - ignoring");
            }
        }
        return size + f.length();
    }

    public static void calculateDiskUsageForProject(AbstractProject project) throws IOException {
        if(DiskUsageProjectActionFactory.DESCRIPTOR.isExcluded(project)) {
            return;
        }
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if (plugin == null) {
            return;
        }
        List<File> exceededFiles = new ArrayList<>();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            addProperty(project);
            property =  (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        }
        Set<DiskUsageBuildInformation> informations = (Set<DiskUsageBuildInformation>) property.getDiskUsage().getBuildDiskUsage(true);
        for(DiskUsageBuildInformation information: informations) {
            exceededFiles.add(new File(Jenkins.get().getBuildDirFor(project), information.getId()));
        }
        if(project instanceof ItemGroup) {
            List<AbstractProject> projects = getAllProjects((ItemGroup) project);
            for(AbstractProject p: projects) {
                exceededFiles.add(p.getRootDir());
            }
        }
        long buildSize = DiskUsageUtil.getFileSize(project.getRootDir(), exceededFiles, 0, plugin.getConfiguration().getMaxDepthDirTraverse());
        Long diskUsageWithoutBuilds = property.getDiskUsageWithoutBuilds();
        boolean update = false;
        if((diskUsageWithoutBuilds <= 0) ||
            (Math.abs(diskUsageWithoutBuilds - buildSize) > 1024)) {
            property.setDiskUsageWithoutBuilds(buildSize);
            update = true;
        }
        if(plugin.getConfiguration().warnAboutJobExceetedSize() && buildSize > plugin.getConfiguration().getJobExceedSize()) {
            try {
                sendEmail("Job " + project.getDisplayName() + " exceeds size", "Job " + project.getDisplayName() + " has size " + getSizeString(buildSize) + ".");
            } catch (MessagingException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting job size.", ex);
            }
        }
        if(update) {
            property.saveDiskUsage();
        }
    }

    public static void calculateDiskUsageForBuild(String buildId, AbstractProject project)
        throws IOException {
        if(DiskUsageProjectActionFactory.DESCRIPTOR.isExcluded(project)) {
            return;
        }
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if (plugin == null) {
            return;
        }
        // Build disk usage has to be always recalculated to be kept up-to-date 
        // - artifacts might be kept only for the last build and users sometimes delete files manually as well.
        long buildSize = DiskUsageUtil.getFileSize(new File(Jenkins.get().getBuildDirFor(project), buildId), new ArrayList<>(), 0, plugin.getConfiguration().getMaxDepthDirTraverse());

        Collection<AbstractBuild> loadedBuilds = project._getRuns().getLoadedBuilds().values();
        AbstractBuild build = null;
        for(AbstractBuild b: loadedBuilds) {
            if(b.getId().equals(buildId)) {
                build = b;
                break;
            }
        }
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            addProperty(project);
            property =  (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        }
        DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(buildId);
        Long size = property.getDiskUsageOfBuild(buildId);
        if((size <= 0) || (Math.abs(size - buildSize) > 1024)) {
            if(information != null) {
                information.setSize(buildSize);
            }
            else {
                if(build != null) {
                    information = new DiskUsageBuildInformation(buildId, build.getTimeInMillis(), build.getNumber(), buildSize);
                    property.getDiskUsage().addBuildInformation(information, build);
                }
                else {
                    // should not happen
                    AbstractBuild newLoadedBuild = (AbstractBuild) project._getRuns().getById(buildId);
                    information = new DiskUsageBuildInformation(buildId, newLoadedBuild.getTimeInMillis(), newLoadedBuild.getNumber(), buildSize);
                    property.getDiskUsage().addBuildInformation(information, null);
                }
            }
            property.saveDiskUsage();
        }
        if(plugin.getConfiguration().warnAboutBuildExceetedSize() && buildSize > plugin.getConfiguration().getBuildExceedSize()) {
            try {
                sendEmail("Build with id " + information.getNumber() + " of project " + project.getDisplayName() + " exceeds size", "Build with id " + information.getNumber() + " of project " + project.getDisplayName() + " has size " + getSizeString(buildSize) + ".");
            } catch (MessagingException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting build size.", ex);
            }
        }
    }

    public static Long calculateWorkspaceDiskUsageForPath(FilePath workspace, ArrayList<FilePath> exceeded) throws IOException, InterruptedException {
        Long diskUsage = 0L;
        if(workspace.exists()) {
            try {
                DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
                int minutes = plugin == null ? 5 : plugin.getConfiguration().getTimeoutWorkspace();
                int maxDepthDirTraverse = plugin == null ? -1 : plugin.getConfiguration().getMaxDepthDirTraverse();
                diskUsage = workspace.getChannel().callAsync(new DiskUsageCallable(workspace, exceeded, 0, maxDepthDirTraverse)).get(minutes, TimeUnit.MINUTES);
            }
            catch (Exception e) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage fails to calculate workspace for file path " + workspace.getRemote() + " through channel " + workspace.getChannel(), e);
            }
        }
        return diskUsage;
    }

    public static void calculateWorkspaceDiskUsage(AbstractProject project) throws IOException, InterruptedException {
        if(DiskUsageProjectActionFactory.DESCRIPTOR.isExcluded(project)) {
            return;
        }
        DiskUsageProperty property =  (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            addProperty(project);
            property =  (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        }

        property.checkWorkspaces();
        for(String nodeName: property.getAgentWorkspaceUsage().keySet()) {
            Node node = null;
            if(nodeName.isEmpty()) {
                node = Jenkins.get();
            }
            else {
                node = Jenkins.get().getNode(nodeName);
            }
            if(node == null) {
                // probably does not exists yet
                continue;
            }

            Computer computer = node.toComputer();
            if(computer != null && computer.getChannel() != null) {
                Iterator<String> iterator = property.getAgentWorkspaceUsage().get(nodeName).keySet().iterator();
                while(iterator.hasNext()) {
                    String projectWorkspace = iterator.next();
                    FilePath workspace = new FilePath(computer.getChannel(), projectWorkspace);
                    if(workspace.exists()) {
                        Long diskUsage = property.getAgentWorkspaceUsage().get(node.getNodeName()).get(workspace.getRemote());
                        ArrayList<FilePath> exceededFiles = new ArrayList<>();
                        if(project instanceof ItemGroup) {
                            List<AbstractProject> projects = getAllProjects((ItemGroup) project);
                            for(AbstractProject p: projects) {
                                DiskUsageProperty prop = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                                if(prop == null) {
                                    prop = new DiskUsageProperty();
                                    p.addProperty(prop);
                                }
                                prop.checkWorkspaces();
                                Map<String, Long> paths = prop.getAgentWorkspaceUsage().get(node.getNodeName());
                                if(paths != null && !paths.isEmpty()) {
                                    for(String path: paths.keySet()) {
                                        exceededFiles.add(new FilePath(node.getChannel(), path));
                                    }
                                }
                            }
                        }
                        diskUsage = calculateWorkspaceDiskUsageForPath(workspace, exceededFiles);
                        if(diskUsage != null && diskUsage > 0) {
                            property.putAgentWorkspaceSize(node, workspace.getRemote(), diskUsage);
                        }
                        controlWorkspaceExceedSize(project);
                    }
                    else {
                        property.remove(node, projectWorkspace);
                    }
                }
            }
        }
        property.saveDiskUsage();
    }

    public static List<AbstractProject> getAllProjects(ItemGroup<? extends Item> itemGroup) {
        List<AbstractProject> items = new ArrayList<>();
        for(Item item: itemGroup.getItems()) {
            if(item instanceof AbstractProject) {
                items.add((AbstractProject) item);
            }
            if(item instanceof ItemGroup) {
                items.addAll(getAllProjects((ItemGroup) item));
            }
        }
        return items;
    }

    /**
     * A {@link Callable} which computes disk usage of remote file object
     */
    public static class DiskUsageCallable implements Callable<Long, IOException> {

        private static final long serialVersionUID = 1;

        public static final Logger LOGGER = Logger
            .getLogger(DiskUsageCallable.class.getName());

        private FilePath path;
        private List<FilePath> exceedFilesPath;
        private int curDirDepth;
        private final int maxDepthDirTraverse;

        public DiskUsageCallable(FilePath filePath, List<FilePath> exceedFilesPath, int curDirDepth, int maxDepthDirTraverse) {
            this.path = filePath;
            this.exceedFilesPath = exceedFilesPath;
            this.curDirDepth = curDirDepth;
            this.maxDepthDirTraverse = maxDepthDirTraverse;
        }

        public Long call() throws IOException {
            File f = new File(path.getRemote());
            List<File> exceeded = new ArrayList<>();
            for(FilePath file: exceedFilesPath) {
                exceeded.add(new File(file.getRemote()));
            }
            return DiskUsageUtil.getFileSize(f, exceeded, curDirDepth + 1, maxDepthDirTraverse);
        }

        @Override
        public void checkRoles(RoleChecker rc) throws SecurityException {

        }

    }

    public static final Logger LOGGER = Logger.getLogger(DiskUsageUtil.class.getName());
}
