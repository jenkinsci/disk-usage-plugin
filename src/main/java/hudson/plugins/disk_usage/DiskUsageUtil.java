/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.FilePath;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.*;
import hudson.plugins.disk_usage.unused.DiskUsageItemGroup;
import hudson.remoting.Callable;
import hudson.tasks.Mailer;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.remoting.RoleChecker;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageUtil {

    public static DiskUsageProperty getDiskUsageProperty(Job job) {
        DiskUsageProperty property = (DiskUsageProperty) job.getProperty(DiskUsageProperty.class);

        if(property==null){
            try {
                property = addProperty(job);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if(property==null){
            //adding failed so set it only from property side
            property = new DiskUsageProperty();
            property.setOwner(job);

        }
        return property;
    }

    private static DiskUsageProperty tryAddProperty(final DiskUsageProperty property, final Job job) {
        java.util.concurrent.Callable<DiskUsageProperty> call = new java.util.concurrent.Callable<DiskUsageProperty>(){

            public DiskUsageProperty call() throws IOException{
                job.addProperty(property);
                return property;
            }
        };
        //call it as future to not cause deadlock like JENKINS-33219
        Future<DiskUsageProperty> f = Timer.get().submit(call);
        //call it as future to not cause deadlock like JENKINS-33219
        DiskUsageProperty p = null;
        try {
            System.err.println(new GregorianCalendar().getTime());

            p = f.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println(new GregorianCalendar().getTime());
            LOGGER.log(Level.FINEST,e.getMessage(), e);
        } catch (ExecutionException e) {
            System.err.println(new GregorianCalendar().getTime());
            LOGGER.log(Level.FINEST, e.getMessage(), e);
        } catch (TimeoutException e) {
            System.err.println(new GregorianCalendar().getTime());
            LOGGER.log(Level.FINEST,e.getMessage(), e);
        }


        return p;
    }
    
    public static DiskUsageProperty addProperty(Item item) throws Exception {
        DiskUsageProperty property = null;
            if(item instanceof Job){
                final Job project = (Job) item;
                property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
                if(property==null){
                    try {
                        final DiskUsageProperty newProperty = new DiskUsageProperty();
                        //better than synchronize call it as future with time out
                        property = tryAddProperty(newProperty, project);

                    } catch (Exception ex) {
                        Logger.getLogger(DiskUsageItemListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                loadData(property, false);
            }
            if(item instanceof ItemGroup){
                
                for(AbstractProject project : DiskUsageUtil.getAllProjects((ItemGroup<?>)item)){
                    DiskUsageProperty p = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
                    if(p==null){
                        try {
                            final DiskUsageProperty newProperty = new DiskUsageProperty();
                            //better than synchronize call it as future with time out
                            property = tryAddProperty(newProperty, project);
                        } catch (Exception ex) {
                            Logger.getLogger(DiskUsageItemListener.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    loadData(p, false);
                }
            }
            return property; 
    }
    
    protected static void loadData(DiskUsageProperty property, boolean loadAllBuilds){
        if(property==null){
            return;
        }
        if(loadAllBuilds){
            try {
                property.getDiskUsage().loadAllBuilds(loadAllBuilds);
            } catch (IOException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else{
            property.loadDiskUsage();
        }
    }
    
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
    
    public static String formatTimeInMilisec(long time){
        if(time/1000<1){
            return "0 seconds";
        }
        long inMinutes = time/60000;
        long hours = inMinutes/60;
        String formatedTime = "";
        if(hours>0){
            String unit = hours>1? "hours" : "hour";
            formatedTime = hours + " " + unit;
        }
        long minutes = inMinutes - hours*60;
        if(minutes>0){
            String unit = minutes>1? "minutes" : "minute";
            formatedTime = formatedTime+ " " + minutes+ " " + unit;
        }
        long seconds = (time/1000) - minutes*60 - hours*60*60;
        if(seconds>0){
            String unit = minutes>1? "seconds" : "second";
            formatedTime = formatedTime+ " " + seconds+ " " + unit;
        }
        return formatedTime;
    }
    
    public static void sendEmail(String subject, String message) throws MessagingException{
       
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        String address = plugin.getConfiguration().getEmailAddress();
        if(address==null || address.isEmpty()){
            Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "e-mail addres is not set for notification about exceed disk size. Please set it in global configuration.");
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
    
    public static Long getSizeInBytes(String stringSize){
        if(stringSize==null || stringSize.equals("-"))
            return 0l;
        String []values = stringSize.split(" ");
        int index = getIndex(values[1]);
        Long value = Long.decode(values[0]);
        Double size = value * (Math.pow(1024, index));       
        return Math.round(size);        
    }
    
    public static void controlAllJobsExceedSize() throws IOException{
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        plugin.refreshGlobalInformation();
        Long allJobsSize = plugin.getCashedGlobalJobsDiskUsage();
        Long exceedJobsSize = plugin.getConfiguration().getAllJobsExceedSize();
        if(allJobsSize>exceedJobsSize){
            try {
                sendEmail("Jobs exeed size", "Jobs exceed size " + getSizeString(exceedJobsSize) + ". Their size is now " + getSizeString(allJobsSize));
            } catch (MessagingException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting build size.", ex);
            }
        }          
    }
    
    public static void controlWorkspaceExceedSize(AbstractProject project){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        DiskUsageProperty property = getDiskUsageProperty(project);
        Long size = property.getAllWorkspaceSize();
        if(plugin.getConfiguration().warnAboutJobWorkspaceExceedSize() && size>plugin.getConfiguration().getJobWorkspaceExceedSize()){
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
    
    public static List<String> parseExcludedJobsFromString(String jobs){
        List<String> list = new ArrayList<String>();
        String jobNames[] = jobs.split(",");
        for(String name: jobNames){
            name = name.trim();
            Item item = Jenkins.getInstance().getItem(name);
            if(item!=null && item instanceof AbstractProject)
                list.add(name);
        }
        return list;
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
        if(number==0)
            return 0;
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
    
    /**
     * Calculate disk usage of build after its execution (or as post-build step)
     * @param build
     * @param listener 
     */
    public static void calculationDiskUsageOfBuild(AbstractBuild build, TaskListener listener){
        if(DiskUsageProjectActionFactory.DESCRIPTOR.isExcluded(build.getProject())){
            listener.getLogger().println("This job is excluded from disk usage calculation.");
            return;
        }
        try{
            //count build.xml too
            build.save();
            DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
            listener.getLogger().println("Started calculate disk usage of build");
            Long startTimeOfBuildCalculation = System.currentTimeMillis();
                DiskUsageUtil.calculateDiskUsageForBuild(build.getId(), build.getProject());
                listener.getLogger().println("Finished Calculation of disk usage of build in " + DiskUsageUtil.formatTimeInMilisec(System.currentTimeMillis() - startTimeOfBuildCalculation));
                DiskUsageProperty property = getDiskUsageProperty(build.getProject());
                if(build.getWorkspace()!=null){
                    ArrayList<FilePath> exceededFiles = new ArrayList<FilePath>();
                    AbstractProject project = build.getProject();
                    Node node = build.getBuiltOn();
                    if(project instanceof ItemGroup){
                        List<AbstractProject> projects = DiskUsageUtil.getAllProjects((ItemGroup) project);
                        for(AbstractProject p: projects){
                            DiskUsageProperty prop = getDiskUsageProperty(p);
                            prop.checkWorkspaces();
                            Map<String,Long> paths = prop.getSlaveWorkspaceUsage().get(node.getNodeName());
                            if(paths!=null && !paths.isEmpty()){
                                for(String path: paths.keySet()){
                                    exceededFiles.add(new FilePath(node.getChannel(),path));
                                }
                            }
                        }
                    }
                    property.checkWorkspaces();
                    listener.getLogger().println("Started calculate disk usage of workspace");
                    Long startTimeOfWorkspaceCalculation = System.currentTimeMillis();
                    Long size = DiskUsageUtil.calculateWorkspaceDiskUsageForPath(build.getWorkspace(),exceededFiles);
                    listener.getLogger().println("Finished Calculation of disk usage of workspace in " + DiskUsageUtil.formatTimeInMilisec(System.currentTimeMillis() - startTimeOfWorkspaceCalculation));
                    property.putSlaveWorkspaceSize(build.getBuiltOn(), build.getWorkspace().getRemote(), size);
                    property.saveDiskUsage();
                    DiskUsageUtil.controlWorkspaceExceedSize(project);
                    property.saveDiskUsage();
                }
            }
            catch(Exception ex){
                listener.getLogger().println("Disk usage plugin fails during calculation disk usage of this build.");
                    Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin fails during build calculation disk space of job " + build.getParent().getDisplayName(), ex);
            }
    }
    
    public static boolean isSymlink(File f) throws IOException{
        return Util.isSymlink(f);
    }
    
    public static Long getFileSize(File f, List<File> exceedFiles) throws IOException {
            long size = 0;
            if(!f.exists())
                return size;
            if (f.isDirectory() && !isSymlink(f)) {
            	File[] fileList = f.listFiles();
            	if (fileList != null) for (File child : fileList) {
                    if(exceedFiles.contains(child))
                        continue; //do not count exceeded files
                    if (!isSymlink(child)) size += getFileSize(child, exceedFiles);
                }
                else {
            		LOGGER.info("Failed to list files in " + f.getPath() + " - ignoring");
            	}
            }
            return size + f.length();
   }
 
    public static void calculateDiskUsageForProject(AbstractProject project) throws Exception{
        if(DiskUsageProjectActionFactory.DESCRIPTOR.isExcluded(project))
            return;
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        List<File> exceededFiles = new ArrayList<File>();       
        DiskUsageProperty property = getDiskUsageProperty(project);
         if(property==null){
            addProperty(project);
            property =  getDiskUsageProperty(project);
        }
        exceededFiles.add(project.getBuildDir());
        //load all - force loading all builds to check state
        Set<DiskUsageBuildInformation> information = (Set<DiskUsageBuildInformation>) property.getDiskUsage().getBuildDiskUsage(true);
        //calculate not loaded builds
        for(String build : property.getDiskUsage().getNotLoadedBuilds()){
            File notLoadedBuild = new File(project.getBuildDir(),build);
            Long size = DiskUsageUtil.getFileSize(notLoadedBuild, new ArrayList<File>());
            property.getDiskUsage().addNotLoadedBuild(notLoadedBuild, size);
        }
        if(project instanceof ItemGroup){
            List<AbstractProject> projects = getAllProjects((ItemGroup) project);
            for(AbstractProject p: projects){
                    exceededFiles.add(p.getRootDir());
            }
        }
        long buildSize = project.getBuildDir().length();
        
        buildSize += DiskUsageUtil.getFileSize(project.getRootDir(), exceededFiles);
        Long diskUsageWithoutBuilds = property.getDiskUsageWithoutBuildDirectory();
        boolean update = false;
        	if (( diskUsageWithoutBuilds <= 0 ) ||
        			( Math.abs(diskUsageWithoutBuilds - buildSize) > 1024 )) {
        		property.setDiskUsageWithoutBuilds(buildSize);
        		update = true;
        	}
                if(plugin.getConfiguration().warnAboutJobExceetedSize() && buildSize>plugin.getConfiguration().getJobExceedSize()){
            try {
                sendEmail("Job " + project.getDisplayName() + " exceeds size", "Job " + project.getDisplayName() + " has size " + getSizeString(buildSize) + ".");
            } catch (MessagingException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting job size.", ex);
            }
                }
        if (update) {
        	property.saveDiskUsage();
        }
    }   
      
    
        public static void calculateDiskUsageForBuild(String buildId, AbstractProject project)
            throws Exception {
            if(DiskUsageProjectActionFactory.DESCRIPTOR.isExcluded(project))
                return;
            DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        //Build disk usage has to be always recalculated to be kept up-to-date 
        //- artifacts might be kept only for the last build and users sometimes delete files manually as well.
        long buildSize = DiskUsageUtil.getFileSize(new File(Jenkins.getInstance().getBuildDirFor(project), buildId), new ArrayList<File>());
        //        if (build instanceof MavenModuleSetBuild) {
//            Collection<List<MavenBuild>> builds = ((MavenModuleSetBuild) build).getModuleBuilds().values();
//            for (List<MavenBuild> mavenBuilds : builds) {
//                for (MavenBuild mavenBuild : mavenBuilds) {
//                    calculateDiskUsageForBuild(mavenBuild);
//                }
//            }
//      }
        Collection<AbstractBuild> loadedBuilds = project._getRuns().getLoadedBuilds().values();
        AbstractBuild build = null;
        for(AbstractBuild b : loadedBuilds){
            if(b.getId().equals(buildId)){
                build = b;
                break;
                //addBuildDiskUsageAction(build);
            }
        }
        DiskUsageProperty property = getDiskUsageProperty(project);
        DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(buildId);
        Long size = property.getDiskUsageOfBuild(buildId);
        if (( size <= 0 ) || ( Math.abs(size - buildSize) > 1024 )) {
                    if(information!=null){
                        information.setSize(buildSize);
                        if(build!=null){
                            information.setLockState(build.isKeepLog());
                        }
                    }
                    else{
                        if(build!=null){
                            information = new DiskUsageBuildInformation(buildId, build.getTimeInMillis(), build.getNumber(), buildSize, build.isKeepLog());
                            property.getDiskUsageOfBuilds().add(information);
                        }
                        else{
                            //should not happen
                            AbstractBuild newLoadedBuild = (AbstractBuild) project._getRuns().getById(buildId);
                            information = new DiskUsageBuildInformation(buildId, newLoadedBuild.getTimeInMillis(), newLoadedBuild.getNumber(), buildSize, build.isKeepLog());
                            property.getDiskUsageOfBuilds().add(information);
                        }
                    }
                    property.saveDiskUsage();
        }
        if(plugin.getConfiguration().warnAboutBuildExceetedSize() && buildSize>plugin.getConfiguration().getBuildExceedSize()){
            try {
                sendEmail("Build with id " + information.getNumber() + " of project " + project.getDisplayName() + " exceeds size", "Build with id " + information.getNumber() + " of project " + project.getDisplayName() + " has size " + getSizeString(buildSize) + ".");
            } catch (MessagingException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage plugin can not send notification about exceeting build size.", ex);
            }
        }
    }
        
    public static Long calculateWorkspaceDiskUsageForPath(FilePath workspace, ArrayList<FilePath> exceeded) throws IOException, InterruptedException{
        Long diskUsage = 0l;
        if(workspace.exists()){
            try{
                diskUsage = workspace.getChannel().callAsync(new DiskUsageCallable(workspace, exceeded)).get(Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().getTimeoutWorkspace(), TimeUnit.MINUTES);             
            }
            catch(Exception e){
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.WARNING, "Disk usage fails to calculate workspace for file path " + workspace.getRemote() + " through channel " + workspace.getChannel(),e);
            }
        }
        return diskUsage;
    }
    
    public static void calculateWorkspaceDiskUsage(AbstractProject project) throws Exception {
        if(DiskUsageProjectActionFactory.DESCRIPTOR.isExcluded(project))
            return;
        DiskUsageProperty property =  getDiskUsageProperty(project);
        
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
                                DiskUsageProperty prop = getDiskUsageProperty(p);
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
                        controlWorkspaceExceedSize(project);
                    }
                    else{
                        property.remove(node, projectWorkspace);
                    }
                }
            }
        }
        property.saveDiskUsage();
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
    
//    public static void calculateNotUsedData() throws IOException{
//        checkUnloadedDataForItemGroup(Jenkins.getInstance(),null); 
//    }
    
     public static boolean isJobLoaded(ItemGroup itemGroup, String directoryName){
        for(Item item : (Collection<Item>)itemGroup.getItems()){
           if(itemGroup.getRootDirFor(item).getName().equals(directoryName)){
               return true;
           }
        }
        return false;
    }
     
     public static void calculateDiskUsageNotLoadedJobs(ItemGroup group){
         DiskUsageItemGroup diskUsage = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getDiskUsageItemGroup(group);
         File jobsDirectory = diskUsage.getJobDirectory();
         if(jobsDirectory == null)
             return;
         Collection<Item> items = group.getItems();
         List<File> excluded = new ArrayList<File>();
         for(Item i : items){
             excluded.add(i.getRootDir());
             if(i instanceof ItemGroup){
                 ItemGroup subGroup = (ItemGroup) i;
                 calculateDiskUsageNotLoadedJobs(subGroup);
             }
         }
         List<File> notLoaded = new ArrayList<File>();
        try {
            checkNotLoadedJobs(jobsDirectory, notLoaded, excluded);
        } catch (IOException ex) {
            Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.SEVERE, null, ex);
        }
        Long size = 0L;
        for(File file: notLoaded){
            try {
                size += getFileSize(file, new ArrayList<File>());
            } catch (IOException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
            diskUsage.setSize(file.getAbsolutePath(), size);
        }
        diskUsage.save();
     }
     
     public static boolean looksLikeJobDirectory(File file){
         File config = new File(file,"config.xml");
         if(config.exists())
             return true;
         File builds = new File(file,"builds");
         if(builds.exists())
             return true;
         for(File child: file.listFiles()){
             if(child.isDirectory())
                 return false;
         }
         return true;
     }
     
     public static boolean looksLikeItemGroupDirectory(File file){
         File config = new File(file,"config.xml");
         if(config.exists())
             return true;
         for(String jobsDirectoryName : DiskUsageItemGroup.JOB_DIRECTORY){
             File jobDir = new File(file,jobsDirectoryName);
             if(jobDir.exists() && jobDir.isDirectory())
                 return true;
         }
         return false;
     }
     
     public static void checkNotLoadedJobs(File file, List<File> notLoadedFiles, List<File> excludedFiles) throws IOException{
         for(File child : file.listFiles()){
             if((Util.isSymlink(child) && !child.exists()) || !child.isDirectory() | excludedFiles.contains(child)){
                 continue;
             }
             if(looksLikeJobDirectory(child) || looksLikeItemGroupDirectory(child)){
                 notLoadedFiles.add(child);
             }
             else{
                 checkNotLoadedJobs(child, notLoadedFiles, excludedFiles);
             }
         }
     }
     
     public static DiskUsageItemGroupAction getItemGroupAction(ItemGroup group){
         if(group instanceof AbstractItem){
                    AbstractItem aItem = (AbstractItem) group;
                    for(Action action : aItem.getAllActions()){
                        if(action instanceof DiskUsageItemGroupAction){
                            DiskUsageItemGroupAction duAction = (DiskUsageItemGroupAction) action;
                            return duAction;
                        }
                    }
         }
         DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
         DiskUsageItemGroup usage = plugin.getDiskUsageItemGroup(group);
         return new DiskUsageItemGroupAction(usage);
     }
     
     public static boolean isContainedInWorkspace(Item item, Node node, String path){
        if(node instanceof Slave){
            Slave slave = (Slave) node;
            return path.contains(slave.getRemoteFS());
        }
        else {
            if (item instanceof TopLevelItem) {
                TopLevelItem topLevelItem = (TopLevelItem) item;
                if (node instanceof Jenkins) {
                    FilePath file = Jenkins.getInstance().getWorkspaceFor(topLevelItem);
                    return path.contains(file.getRemote());
                } else {
                    try {
                        return path.contains(node.getWorkspaceFor(topLevelItem).getRemote());
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
            return false;
        }
    }
     
     
     //serve only for obtaining information about keep log during loading build - matrix plugin in method whyKeepLog scen builds and causes cycle of method onLoad
     protected static Boolean isKeepLog(AbstractBuild build) {
         File file = new File(build.getRootDir(),"config.xml");
         if(file.exists()){
             XmlFile config = new XmlFile(file);
            try {
                return config.asString().contains("<keepLog>true</keepLog>");
            } catch (IOException ex) {
                Logger.getLogger(DiskUsageUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
         }
         return null;
     }
     
     public static DiskUsageItemAction getDiskUsageItemAction(Item item){
        if(item instanceof AbstractProject) {
            AbstractProject project = (AbstractProject) item;
            return project.getAction(ProjectDiskUsageAction.class);
        }
        if(item instanceof ItemGroup){
            return DiskUsageUtil.getItemGroupAction((ItemGroup)item);
        }
        //there is not supported something else
        return null;
    }
     
     public static void actualizeCashedData(Item item){
         DiskUsageItemAction action = getDiskUsageItemAction(item);
         
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

        @Override
        public void checkRoles(RoleChecker rc) throws SecurityException {
           
        }
       
    }
    
    public static final Logger LOGGER = Logger.getLogger(DiskUsageUtil.class.getName());
}
