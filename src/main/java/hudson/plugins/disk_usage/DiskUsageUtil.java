/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.tasks.MailSender;
import hudson.tasks.Mailer;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import jenkins.model.Jenkins;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageUtil {
    
    public static Date getDate(String timeCount, String timeUnit){
        if(timeUnit==null || !timeUnit.matches("\\d+") || !timeCount.matches("\\d+"))
           return null;
        int unit = Integer.decode(timeUnit);
        int count = Integer.decode(timeCount);
        return getDate(unit,count);
    }
    
    public static Date getDate(int unit, int count){
        Calendar calendar = new GregorianCalendar();
        calendar.set(unit, calendar.get(unit)-count);
        return calendar.getTime();
    }
    
    public static void sendEmail(String subject, String message) throws MessagingException{
       
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        String address = plugin.getEmailAddress();
        MimeMessage msg = new MimeMessage(Mailer.descriptor().createSession());
        msg.setSubject(subject);
        msg.setText(message, "utf-8");
        msg.setFrom(new InternetAddress(Mailer.descriptor().getAdminAddress()));
        msg.setSentDate(new Date());
        msg.setRecipient(RecipientType.TO, new InternetAddress(address));
        Transport.send(msg);     
    }
    
    public static Long getSizeInBytes(String stringSize){
        if(stringSize==null || stringSize.equals("-"))
            return 0l;
        String []values = stringSize.split(" ");
        int index = getIndex(values[1]);
        Long value = Long.decode(values[0]);
        Double size = value * (Math.pow(1024, index));       
        return Math.round(size);        
    }
    
    public static void controlAllJobsExceedSize(){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        plugin.refreshGlobalInformation();
        Long allJobsSize = plugin.getCashedGlobalJobsDiskUsage();
        Long exceedJobsSize = plugin.getAllJobsExceedSize();
        if(allJobsSize>exceedJobsSize){
            try {
                sendEmail("Jobs exeed size", "Jobs exceed size " + getSizeString(exceedJobsSize) + ". Their size is now " + getSizeString(allJobsSize));
            } catch (MessagingException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting build size.", ex);
            }
        }          
    }
    
    public static void controlorkspaceExceedSize(AbstractProject project){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        Long size = property.getAllWorkspaceSize();
                        if(plugin.warnAboutJobWorkspaceExceedSize() && size>plugin.getJobWorkspaceExceedSize()){
                            StringBuilder builder = new StringBuilder();
                            builder.append("Workspaces of Job " + project.getDisplayName() + " have size " + size + ".");
                            builder.append("\n");
                            builder.append("List of workspaces:");
                            for(String slaveName : property.getSlaveWorkspaceUsage().keySet()){
                                Long s = 0l;
                                for(Long l :property.getSlaveWorkspaceUsage().get(slaveName).values()){
                                    s += l;
                                }
                                builder.append("\n");
                                builder.append("Slave " + slaveName + " has workspace of job " + project.getDisplayName() + " with size " + getSizeString(s));
                            }
                            try {
                                sendEmail("Workspaces of Job " + project.getDisplayName() + " exceed size", builder.toString());
                            } catch (MessagingException ex) {
                                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting build size.", ex);
                            }
                        }
        
    }
    
    public static String getSizeString(Long size) {
        if (size == null || size <= 0) {
            return "-";
        }

        int floor = (int) getScale(size);
        floor = Math.min(floor, 4);
        double base = Math.pow(1024, floor);
        String unit = getUnitString(floor);

        return Math.round(size / base) + " " + unit;
    }

    public static double getScale(long number) {
        return Math.floor(Math.log(number) / Math.log(1024));
    }
    
    public static int getIndex(String unit){
        int index = 0;
        if(unit.equals("KB"))
            index = 1;
        if(unit.equals("MB"))
            index = 2;
        if(unit.equals("GB"))
            index = 3;        
        if(unit.equals("TB"))
            index = 4;
        return index;
    }

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
    
    public static boolean isSymlink(File f){
        boolean symlink = false;
        try{
            Class<?> files = Thread.currentThread().getContextClassLoader().loadClass( "java.nio.file.Files" );
            Class<?> path = Thread.currentThread().getContextClassLoader().loadClass( "java.nio.file.Path" );
            Class<?> paths = Thread.currentThread().getContextClassLoader().loadClass( "java.nio.file.Paths" );
            URI uri = new URI(f.getAbsolutePath());
            Object filePath = paths.getMethod("get", URI.class).invoke(null, uri);
            symlink = (Boolean) files.getMethod("isSymbolicLink", path).invoke(null, filePath);           
        }
        catch(Exception e){
            //not java 7, try native
            try{
                symlink = Util.isSymlink(f);
            }
            catch(Exception e2){
                //native fails
            }
        }
        return symlink;
    }
    
    public static Long getFileSize(File f, List<File> exceedFiles) throws IOException {
            long size = 0;
            if (f.isDirectory() && !isSymlink(f)) {
            	File[] fileList = f.listFiles();
            	if (fileList != null) for (File child : fileList) {
                    if(exceedFiles.contains(child))
                        continue; //do not count exceeded files
                    if (!Util.isSymlink(child)) size += getFileSize(child, exceedFiles);
                }
                else {
            		LOGGER.info("Failed to list files in " + f.getPath() + " - ignoring");
            	}
            }
            return size + f.length();
   }
    
    public static void calculateDiskUsageForProject(AbstractProject project) throws IOException{
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        List<File> exceededFiles = new ArrayList<File>();
        List<AbstractBuild> builds = project.getBuilds();
        for(AbstractBuild build : builds){
            exceededFiles.add(build.getRootDir());
        }
        if(project instanceof ItemGroup){
            List<AbstractProject> projects = getAllProjects((ItemGroup) project);
            for(AbstractProject p: projects){
                    exceededFiles.add(p.getRootDir());
            }
        }
        long buildSize = DiskUsageUtil.getFileSize(project.getRootDir(), exceededFiles);
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property==null){
            property = new DiskUsageProperty();
            project.addProperty(property);
        }
        Long diskUsageWithoutBuilds = property.getDiskUsageWithoutBuilds();
        boolean update = false;
        	if (( diskUsageWithoutBuilds <= 0 ) ||
        			( Math.abs(diskUsageWithoutBuilds - buildSize) > 1024 )) {
        		property.setDiskUsageWithoutBuilds(buildSize);
        		update = true;
        	}
                if(plugin.warnAboutJobExceetedSize() && buildSize>plugin.getJobExceedSize()){
            try {
                sendEmail("Job " + project.getDisplayName() + " exceeds size", "Job " + project.getDisplayName() + " has size " + getSizeString(buildSize) + ".");
            } catch (MessagingException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting job size.", ex);
            }
                }
        if (update) {
        	project.save();
        }
    }


        public static void calculateDiskUsageForBuild(AbstractBuild build)
            throws IOException {
            DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        //Build disk usage has to be always recalculated to be kept up-to-date 
        //- artifacts might be kept only for the last build and users sometimes delete files manually as well.
        long buildSize = DiskUsageUtil.getFileSize(build.getRootDir(), new ArrayList<File>());
//        if (build instanceof MavenModuleSetBuild) {
//            Collection<List<MavenBuild>> builds = ((MavenModuleSetBuild) build).getModuleBuilds().values();
//            for (List<MavenBuild> mavenBuilds : builds) {
//                for (MavenBuild mavenBuild : mavenBuilds) {
//                    calculateDiskUsageForBuild(mavenBuild);
//                }
//            }
//        }
        BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
        boolean updateBuild = false;
        if (action == null) {
            action = new BuildDiskUsageAction(build, buildSize);
            build.addAction(action);
            action.diskUsage = buildSize;
            updateBuild = true;
        } 
        else {
            if (( action.diskUsage <= 0 ) ||
        			( Math.abs(action.diskUsage - buildSize) > 1024 )) {
        		action.diskUsage = buildSize;
        		updateBuild = true;
            }
        }
                if(plugin.warnAboutBuildExceetedSize() && buildSize>plugin.getBuildExceedSize()){
                    try {
                        sendEmail("Build " + build.getNumber() + " of project " + build.getProject().getDisplayName() + " exceeds size", "Build " + build.getNumber() + " of project " + build.getProject().getDisplayName() + " has size " + getSizeString(buildSize) + ".");
                    } catch (MessagingException ex) {
                        Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting build size.", ex);
                    }
                }
        if ( updateBuild ) {
        	build.save();
        }
    }
        
    public static Long calculateWorkspaceDiskUsageForPath(FilePath workspace, ArrayList<FilePath> exceeded) throws IOException, InterruptedException{
        Long diskUsage = 0l;
        if(workspace.exists()){
            try{
                diskUsage = workspace.getChannel().callAsync(new DiskUsageCallable(workspace, exceeded)).get(Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getWorkspaceTimeOut(), TimeUnit.MILLISECONDS);             
            }
            catch(Exception e){
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage fails to calculate workspace for file path " + workspace.getRemote() + " through channel " + workspace.getChannel(),e);
            }
        }
        return diskUsage;
    }
    
    public static void calculateWorkspaceDiskUsage(AbstractProject project) throws IOException, InterruptedException {
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        DiskUsageProperty property =  (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        if(property==null){
            property = new DiskUsageProperty();
            project.addProperty(property);
        }
        property.checkWorkspaces();
        for(String nodeName: property.getSlaveWorkspaceUsage().keySet()){
            Node node = null;
            if(nodeName.isEmpty()){
                node = Jenkins.getInstance();
            }
            else{
                node = Jenkins.getInstance().getNode(nodeName);
            }
            if(node==null){
                //probably does not exists yet
                continue;
            }
            
            if(node.toComputer()!=null && node.toComputer().getChannel()!=null){
                Iterator<String> iterator = property.getSlaveWorkspaceUsage().get(nodeName).keySet().iterator();
                while(iterator.hasNext()){
                    String projectWorkspace = iterator.next();
                    FilePath workspace = new FilePath(node.toComputer().getChannel(), projectWorkspace);
                    if(workspace.exists()){
                        Long diskUsage = property.getSlaveWorkspaceUsage().get(node.getNodeName()).get(workspace.getRemote());
                        ArrayList<FilePath> exceededFiles = new ArrayList<FilePath>();
                        if(project instanceof ItemGroup){
                            List<AbstractProject> projects = getAllProjects((ItemGroup) project);
                            for(AbstractProject p: projects){
                                DiskUsageProperty prop = (DiskUsageProperty) p.getProperty(DiskUsageProperty.class);
                                if(prop==null){
                                    prop = new DiskUsageProperty();
                                    p.addProperty(prop);
                                }
                                prop.checkWorkspaces();
                                Map<String,Long> paths = prop.getSlaveWorkspaceUsage().get(node.getNodeName());
                                if(paths!=null && !paths.isEmpty()){
                                    for(String path: paths.keySet()){
                                        exceededFiles.add(new FilePath(node.getChannel(),path));
                                    }
                                }
                            }
                        }
                        diskUsage = calculateWorkspaceDiskUsageForPath(workspace, exceededFiles);
                        if(diskUsage!=null && diskUsage>0){
                            property.putSlaveWorkspaceSize(node, workspace.getRemote(), diskUsage);
                        }
                        controlorkspaceExceedSize(project);
                    }
                    else{
                        property.remove(node, projectWorkspace);
                    }
                }
            }
        }
        project.save();
    }
    
    public static List<AbstractProject> getAllProjects(ItemGroup<? extends Item> itemGroup) {
        List<AbstractProject> items = new ArrayList<AbstractProject>();
        for (Item item : itemGroup.getItems()) {
            if(item instanceof AbstractProject){
                items.add((AbstractProject)item);
            }
            if (item instanceof ItemGroup) {
                items.addAll(getAllProjects((ItemGroup) item));
            }
        }
        return items;
    }

    /**
     * A {@link Callable} which computes disk usage of remote file object
     */
    public static class DiskUsageCallable implements Callable<Long, IOException> {

    	public static final Logger LOGGER = Logger
    		.getLogger(DiskUsageCallable.class.getName());

        private FilePath path;
        private List<FilePath> exceedFilesPath;

        public DiskUsageCallable(FilePath filePath, List<FilePath> exceedFilesPath) {
            this.path = filePath;
            this.exceedFilesPath = exceedFilesPath;
        }

        public Long call() throws IOException {
            File f = new File(path.getRemote());
            List<File> exceeded = new ArrayList<File>();
            for(FilePath file: exceedFilesPath){
                exceeded.add(new File(file.getRemote()));
            }
            return DiskUsageUtil.getFileSize(f, exceeded);
        }
       
    }
    
    public static final Logger LOGGER = Logger
    		.getLogger(DiskUsageUtil.class.getName());
}
