package hudson.plugins.disk_usage.integration;


import hudson.Functions;
import hudson.tasks.BatchFile;
import java.util.ConcurrentModificationException;
import java.util.GregorianCalendar;
import hudson.model.FreeStyleBuild;
import hudson.util.XStream2;
import hudson.model.AbstractBuild;
import hudson.matrix.MatrixBuild;
import hudson.model.TopLevelItem;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.Annotation;
import org.jvnet.hudson.test.JenkinsRecipe;
import hudson.XmlFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import hudson.model.AbstractProject;
import java.io.IOException;
import org.jvnet.hudson.test.recipes.LocalData;
import java.io.PrintStream;
import hudson.FilePath;
import hudson.tasks.Shell;
import hudson.plugins.disk_usage.*;
import java.io.File;
import java.util.Map;
import java.util.Set;
import hudson.model.listeners.RunListener;
import hudson.model.Slave;
import org.junit.Test;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.AxisList;
import hudson.matrix.TextAxis;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleProject;
import hudson.slaves.OfflineCause;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.METHOD;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsagePropertyTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
        
    
    @Test
    public void testGetAllDiskUsageWithoutBuilds() throws Exception{
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        MatrixProject matrixProject = j.jenkins.createProject(MatrixProject.class, "project2");
        TextAxis axis1 = new TextAxis("axis", "axisA", "axisB", "axisC");
        TextAxis axis2 = new TextAxis("axis2", "Aaxis", "Baxis", "Caxis");
        AxisList list = new AxisList();
        list.add(axis1);
        list.add(axis2);
        matrixProject.setAxes(list);       
        Long sizeOfProject = 7546l;
        Long sizeOfMatrixProject = 6800l;
        DiskUsageProperty projectProperty = project.getProperty(DiskUsageProperty.class);
        //project.addProperty(projectProperty);
        projectProperty.setDiskUsageWithoutBuilds(sizeOfProject);
        DiskUsageProperty matrixProjectProperty = matrixProject.getProperty(DiskUsageProperty.class);
        matrixProjectProperty.setDiskUsageWithoutBuilds(sizeOfMatrixProject);
        long size1 = 5390;
        int count = 1;
        Long matrixProjectTotalSize = sizeOfMatrixProject;
        for(MatrixConfiguration c: matrixProject.getItems()){
            DiskUsageProperty configurationProperty = new DiskUsageProperty();
            c.addProperty(configurationProperty);
            configurationProperty.setDiskUsageWithoutBuilds(count*size1);  
            matrixProjectTotalSize += count*size1;
            //matrixProjectTotalSize += c.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
            count++;
        }
        assertEquals("DiskUsageProperty for FreeStyleProject " + project.getDisplayName() + " returns wrong value its size without builds and including sub-projects.", sizeOfProject, project.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds());
        assertEquals("DiskUsageProperty for MatrixProject " + project.getDisplayName() + " returns wrong value for its size without builds and including sub-projects.", matrixProjectTotalSize, matrixProject.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds());
    }
    
    @Test
    public void testCheckWorkspaces() throws Exception{
        //turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Slave slave1 = j.createOnlineSlave();
        Slave slave2 = j.createOnlineSlave();
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        project.setAssignedLabel(slave1.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setAssignedLabel(slave2.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.getBuildByNumber(1).delete();
        DiskUsageProperty prop = project.getProperty(DiskUsageProperty.class);
        if(prop == null){
            prop = new DiskUsageProperty();
            project.addProperty(prop);
        }
        prop.checkWorkspaces();
        Set<String> nodes = prop.getSlaveWorkspaceUsage().keySet();
        assertTrue("DiskUsage property should contains slave " + slave2.getDisplayName() + " in slaveWorkspaceUsage.", nodes.contains(slave2.getNodeName()));
        assertFalse("DiskUsage property should not contains slave " + slave1.getDisplayName() + " in slaveWorkspaceUsage when detection of user workspace withour reference from project is not set.", nodes.contains(slave1.getNodeName()));
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().setCheckWorkspaceOnSlave(true);
        prop.checkWorkspaces();
        assertTrue("DiskUsage property should contains slave " + slave2.getDisplayName() + " in slaveWorkspaceUsage.", nodes.contains(slave2.getNodeName()));
        assertTrue("DiskUsage property should contains slave " + slave1.getDisplayName() + " in slaveWorkspaceUsage when detection of user workspace withour reference from project is set.", nodes.contains(slave1.getNodeName()));      
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().setCheckWorkspaceOnSlave(false);
    }
    
    @Test
    public void getWorkspaceSizeTest() throws Exception{
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Slave slave1 = DiskUsageTestUtil.createSlave("slave1", new File(j.jenkins.getRootDir(),"workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        Slave slave2 = DiskUsageTestUtil.createSlave("slave2", new File(j.jenkins.getRootDir(),"workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        project.setAssignedLabel(slave1.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setAssignedLabel(slave2.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(j.jenkins.getRootDir().getAbsolutePath() + "/project-custom-workspace");
        j.buildAndAssertSuccess(project);
        DiskUsageProperty prop = project.getProperty(DiskUsageProperty.class);
        if(prop == null){
            prop = new DiskUsageProperty();
            project.addProperty(prop);
        }
        prop.checkWorkspaces();
        Long workspaceSize = 7509l;
        Map<String,Map<String,Long>> diskUsage = prop.getSlaveWorkspaceUsage();
        for(String name : diskUsage.keySet()){
            Map<String,Long> slaveInfo = diskUsage.get(name);
            for(String path: slaveInfo.keySet()){
                slaveInfo.put(path, workspaceSize);
            }
        }
        assertEquals("DiskUsage workspaces which is configured as slave workspace is wrong.", workspaceSize*2, prop.getWorkspaceSize(true), 0);
        assertEquals("DiskUsage workspaces which is not configured as slave workspace is wrong.", workspaceSize, prop.getWorkspaceSize(false), 0);
    }
    
    @Test
    public void testchcekWorkspacesIfSlaveIsDeleted() throws Exception{
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        DiskUsageProperty property = new DiskUsageProperty();
        project.addProperty(property);
        Slave slave1 = j.createOnlineSlave();
        Slave slave2 = j.createOnlineSlave();
        slave1.getWorkspaceRoot().mkdirs();
        slave2.getWorkspaceRoot().mkdirs();
        FilePath path = j.jenkins.getWorkspaceFor(project);
        path.mkdirs();
        property.putSlaveWorkspaceSize(j.jenkins, path.getRemote(), 10495l);
        property.putSlaveWorkspaceSize(slave1,slave1.getRemoteFS(),5670l);
        property.putSlaveWorkspaceSize(slave2, slave2.getRemoteFS(), 7987l);
        j.jenkins.removeNode(slave2);
        property.checkWorkspaces();
        assertFalse("Disk usage property should not contains slave which does not exist.", property.getSlaveWorkspaceUsage().containsKey(slave2.getNodeName()));
        assertTrue("Disk usage property should contains slave1.", property.getSlaveWorkspaceUsage().containsKey(slave1.getNodeName()));
        assertTrue("Disk usage property should contains jenkins master.", property.getSlaveWorkspaceUsage().containsKey(j.jenkins.getNodeName()));       
    }  
    
    @Test
    public void testchcekWorkspacesIfDoesNotExistsIsDeleted() throws Exception{
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        DiskUsageProperty property = new DiskUsageProperty();
        project.addProperty(property);
        Slave slave1 = j.createOnlineSlave();
        Slave slave2 = j.createOnlineSlave();
        slave1.getWorkspaceRoot().mkdirs();
        slave2.getWorkspaceRoot().mkdirs();
        FilePath path = j.jenkins.getWorkspaceFor(project);
        path.mkdirs();
        property.putSlaveWorkspaceSize(j.jenkins, path.getRemote(), 10495l);
        property.putSlaveWorkspaceSize(slave1, slave1.getRemoteFS() + "/project", 5670l);
        property.putSlaveWorkspaceSize(slave2, slave2.getRemoteFS(), 7987l);
        property.checkWorkspaces();
        assertFalse("Disk usage property should not contains slave which does not have any workspace for its project.", property.getSlaveWorkspaceUsage().containsKey(slave1.getNodeName()));
        assertTrue("Disk usage property should contains slave2.", property.getSlaveWorkspaceUsage().containsKey(slave2.getNodeName()));
        assertTrue("Disk usage property should contains jenkins master.", property.getSlaveWorkspaceUsage().containsKey(j.jenkins.getNodeName()));       
        path.delete();
        property.checkWorkspaces();
        assertFalse("Disk usage property should contains jenkins master, because workspace for its project was deleted.", property.getSlaveWorkspaceUsage().containsKey(j.jenkins.getNodeName()));       
        
    }
    
    @Test
    public void testGetAllNonSlaveOrCustomWorkspaceSizeWithOnlySlaves() throws Exception{
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        if (Functions.isWindows()){
            project.getBuildersList().add(new BatchFile("echo hello > log"));
        } else {
            project.getBuildersList().add(new Shell("echo hello > log"));
        }
        Slave slave3 = DiskUsageTestUtil.createSlave("slave3", new File(j.jenkins.getRootDir(),"SlaveWorkspace").getPath(), j.jenkins, j.createComputerLauncher(null));
        Slave slave1 = j.createOnlineSlave();
        Slave slave2= j.createOnlineSlave();
        File workspaceSlave1 = new File(slave3.getRemoteFS(), project.getName()+ "/log");
        //DiskUsageTestUtil.createFileWithContent(workspaceSlave1);
        File workspaceSlave2 = new File(slave1.getRemoteFS(), project.getName() + "/log");
        //DiskUsageTestUtil.createFileWithContent(workspaceSlave2);
        File customWorkspaceSlave1 = new File(j.jenkins.getRootDir(),"custom2/log");
        //DiskUsageTestUtil.createFileWithContent(customWorkspaceSlave1);
        File customWorkspaceSlave2 = new File(j.jenkins.getRootDir(),"custom1/log");
        //DiskUsageTestUtil.createFileWithContent(customWorkspaceSlave2);
        project.setAssignedLabel(slave3.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(customWorkspaceSlave1.getParentFile().getAbsolutePath());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(null);
        project.setAssignedLabel(slave2.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(customWorkspaceSlave2.getParentFile().getAbsolutePath());
        j.buildAndAssertSuccess(project);
        Long customWorkspaceSlaveSize = customWorkspaceSlave1.length() + customWorkspaceSlave2.length() + customWorkspaceSlave1.getParentFile().length() + customWorkspaceSlave2.getParentFile().length();
        assertEquals("", customWorkspaceSlaveSize, project.getProperty(DiskUsageProperty.class).getAllNonSlaveOrCustomWorkspaceSize(), 0);
        //take one slave offline
        slave1.toComputer().disconnect(new OfflineCause.ByCLI("test disconnection"));
        assertEquals("", customWorkspaceSlaveSize, project.getProperty(DiskUsageProperty.class).getAllNonSlaveOrCustomWorkspaceSize(), 0);
    }
    
    @Test
    public void testGetAllNonSlaveOrCustomWorkspaceSizeWithMaster() throws Exception{
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        if (Functions.isWindows()){
            project.getBuildersList().add(new BatchFile("echo hello > log"));
        } else {
            project.getBuildersList().add(new Shell("echo hello > log"));
        }
        Slave slave1 = j.createOnlineSlave();
        File workspaceSlave2 = new File(slave1.getRemoteFS(), project.getName() + "/log");
        File customWorkspaceSlave1 = new File(j.jenkins.getRootDir(),"custom2/log");
        File customWorkspaceSlave2 = new File(j.jenkins.getRootDir(),"custom1/log");
        j.jenkins.setNumExecutors(1);
        project.setAssignedLabel(j.jenkins.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(customWorkspaceSlave1.getParentFile().getAbsolutePath());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(null);
        project.setAssignedLabel(slave1.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(customWorkspaceSlave2.getParentFile().getAbsolutePath());
        j.buildAndAssertSuccess(project);
        Long customWorkspaceSlaveSize = customWorkspaceSlave1.length() + customWorkspaceSlave2.length() + customWorkspaceSlave1.getParentFile().length() + customWorkspaceSlave2.getParentFile().length();
        assertEquals("", customWorkspaceSlaveSize, project.getProperty(DiskUsageProperty.class).getAllNonSlaveOrCustomWorkspaceSize(), 0);
        //take one slave offline
        j.jenkins.setNumExecutors(0);
        assertEquals("", customWorkspaceSlaveSize, project.getProperty(DiskUsageProperty.class).getAllNonSlaveOrCustomWorkspaceSize(), 0);
    }
    
    @Test
    @LocalData
    public void testBackwadrCompatibility1() throws IOException{
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().disableBuildsDiskUsageCalculation();
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().disableJobsDiskUsageCalculation();
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().disableWorkspacesDiskUsageCalculation();
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        property.getDiskUsage().loadAllBuilds();
        assertEquals("Size of project1 should be loaded from previous configuration.", 188357L, property.getAllDiskUsageWithoutBuilds(), 0);
        assertEquals("Size of build 2 should be loaded from previous configuration.", 23932L, property.getDiskUsageOfBuild(2), 0);
    }
    
    
    @Test
    @ReplaceHudsonHomeWithCurrentPath("jobs/project1/config.xml")
    @LocalData
    public void testBackwadrCompatibility2() throws IOException{
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().disableBuildsDiskUsageCalculation();
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().disableJobsDiskUsageCalculation();
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().disableWorkspacesDiskUsageCalculation();
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        property.getDiskUsage().loadAllBuilds();
        assertEquals("Size of project1 should be loaded from previous configuration.", 188357L, property.getAllDiskUsageWithoutBuilds(), 0);
        assertEquals("Size of workspaces should be loaded from previous configuration.", 4096L, property.getAllWorkspaceSize(), 0);
        assertTrue("Path of workspace shoudl be loaded form previous configuration.", property.getSlaveWorkspaceUsage().get("").containsKey(j.jenkins.getRootDir().getAbsolutePath() + "/workspace"));
    }
    
    @Test
    @LocalData
    public void testGetDiskUsageOfBuilds(){
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        for(DiskUsageBuildInformation information : property.getDiskUsageOfBuilds()){
            assertEquals("Disk usage of build has loaded wrong size.", information.getNumber()*1000, information.getSize(), 0);
        }
        assertEquals("No build should be loaded.", loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0);
     }
     
    
    @Test
    @LocalData
     public void testGetDiskUsageOfBuild(){
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertEquals("Build with id 1 should have size 3000", 3000, property.getDiskUsageOfBuild("1"), 0);
        assertEquals("Build with id 7 should have size 10000", 10000, property.getDiskUsageOfBuild("7"), 0);
        assertEquals("No build should be loaded.", loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0);
     }
     
    @Test
    @LocalData
     public void testGetDiskUsageBuildInformation(){
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertEquals("Build with id 1 should have size 3000", 3000, property.getDiskUsageBuildInformation("1").getSize(), 0);
        assertEquals("Build with id 7 should have size 10000", 10000, property.getDiskUsageBuildInformation("7").getSize(), 0);
        assertEquals("No build should be loaded.", loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0);
     }
     
    @Test
    @LocalData
     public void testGetDiskUsageOfBuildByNumber(){
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertEquals("Build with id 1 should have size 3000", 3000, property.getDiskUsageOfBuild(1), 0);
        assertEquals("Build with id 7 should have size 10000", 10000, property.getDiskUsageOfBuild(7), 0);
        assertEquals("No build should be loaded.", loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0);
    
     }

    @Test
    @ReplaceHudsonHomeWithCurrentPath("jobs/project1/disk-usage.xml")
    @LocalData
    public void testCheckWorkspacesBuildsWithoutLoadingBuilds() throws IOException, InterruptedException {
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        FilePath f = j.jenkins.getWorkspaceFor((TopLevelItem)project);
        property.checkWorkspaces();
        assertEquals("Workspace should have size 4096", 4096, property.getAllWorkspaceSize(), 0);
        assertEquals("No build should be loaded.", loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0);
    }
    
    @Test
    @ReplaceHudsonHomeWithCurrentPath("jobs/project1/config.xml, jobs/project1/builds/1/build.xml, jobs/project1/builds/3/build.xml")
    @LocalData
    public void testCheckWorkspacesWithLoadingBuilds() throws IOException {
       File file = new File(j.jenkins.getRootDir(),"jobs/project2/builds/1/build.xml");
       XmlFile f = new XmlFile(new XStream2(), file);
       String newBuildXml = f.asString().replace("${JENKINS_HOME}",j.jenkins.getRootDir().getAbsolutePath());
       PrintStream st = new PrintStream(file);
       st.print(newBuildXml);
       AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
       AbstractProject project2 = (AbstractProject) j.jenkins.getItem("project2");
       DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
       DiskUsageProperty property2 = (DiskUsageProperty) project2.getProperty(DiskUsageProperty.class);
       property2.getDiskUsage().loadAllBuilds();
       assertTrue("Project should contains workspace with path {JENKINS_HOME}/jobs/project1/workspace", property.getSlaveWorkspaceUsage().get("").containsKey("${JENKINS_HOME}/jobs/project1/workspace"));
       assertTrue("Project should contains workspace with path {JENKINS_HOME}/workspace", property2.getSlaveWorkspaceUsage().get("").containsKey(j.jenkins.getRootDir().getAbsolutePath() + "/workspace"));      
   
       assertEquals("Builds should be loaded.", 2, project2._getRuns().getLoadedBuilds().size(), 0);
    }
    
    @Test
    public void testGetAllDiskUsageOfBuild() throws IOException, Exception{
;        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        MatrixProject matrixProject = j.jenkins.createProject(MatrixProject.class, "project2");
        TextAxis axis1 = new TextAxis("axis", "axisA", "axisB", "axisC");
        TextAxis axis2 = new TextAxis("axis2", "Aaxis", "Baxis", "Caxis");
        AxisList list = new AxisList();
        list.add(axis1);
        list.add(axis2);
        matrixProject.setAxes(list);
        j.buildAndAssertSuccess(project);
        AbstractBuild build = project.getLastBuild();
        j.buildAndAssertSuccess(matrixProject);
        MatrixBuild matrixBuild1 = matrixProject.getLastBuild();
        j.buildAndAssertSuccess(matrixProject);
        MatrixBuild matrixBuild2 = matrixProject.getLastBuild();
        Long sizeofBuild = 7546l;
        Long sizeOfMatrixBuild1 = 6800l;
        Long sizeOfMatrixBuild2 = 14032l;
        DiskUsageTestUtil.getBuildDiskUsageAction(build).setDiskUsage(sizeofBuild);
        DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild1).setDiskUsage(sizeOfMatrixBuild1);
        DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild2).setDiskUsage(sizeOfMatrixBuild2);
        long size1 = 5390;
        long size2 = 2390;
        int count = 1;
        Long matrixBuild1TotalSize = sizeOfMatrixBuild1;
        Long matrixBuild2TotalSize = sizeOfMatrixBuild2;
        for(MatrixConfiguration c: matrixProject.getItems()){
            AbstractBuild configurationBuild = c.getBuildByNumber(1);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild).setDiskUsage(count*size1);
            matrixBuild1TotalSize += count*size1;
            AbstractBuild configurationBuild2 = c.getBuildByNumber(2);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild2).setDiskUsage(count*size2);
            matrixBuild2TotalSize += count*size2;
            count++;
        }
        hudson.plugins.disk_usage.DiskUsageProperty freeStyleProjectProperty = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        DiskUsageProperty matrixProjectProperty = (DiskUsageProperty) matrixProject.getProperty(DiskUsageProperty.class);
        assertEquals("BuildDiskUsageAction for build 1 of FreeStyleProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.", sizeofBuild, freeStyleProjectProperty.getAllDiskUsageOfBuild(1));
        assertEquals("BuildDiskUsageAction for build 1 of MatrixProject " + matrixProject.getDisplayName() + " returns wrong value for its size including sub-builds.", matrixBuild1TotalSize, matrixProjectProperty.getAllDiskUsageOfBuild(1));
        assertEquals("BuildDiskUsageAction for build 2 of MatrixProject " + matrixProject.getDisplayName() + " returns wrong value for its size including sub-builds.", matrixBuild2TotalSize, matrixProjectProperty.getAllDiskUsageOfBuild(2));
        
    }
    
    @Test
    @LocalData
    public void testDoNotBreakLazyLoading(){
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        assertTrue("This tests does not sense if there are loaded all builds.",8>loadedBuilds);
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertEquals("Size of builds should be loaded.", 1000, property.getAllDiskUsageOfBuild(8), 0);
        assertEquals("Size of builds should be loaded.", 7000, property.getAllDiskUsageOfBuild(4), 0);
        assertTrue("No new build should be loaded.", loadedBuilds <= project._getRuns().getLoadedBuilds().size());      
    }
    
    @Test
    public void testRemoveBuild() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        assertEquals("Disk usage should have information about two builds.", 2, property.getDiskUsage().getBuildDiskUsage(false).size());
        AbstractBuild build = project.getLastBuild();
        build.delete();
        assertEquals("Deleted build should be removed from disk-usage informations too.", 1, property.getDiskUsage().getBuildDiskUsage(false).size());
    }
    
    @Test
    public void testRemoveDeletedBuildNotLoadedByJenkins() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        AbstractBuild build = project.getLastBuild();
        File file = build.getRootDir();
        FilePath path = new FilePath(file);
        path.deleteRecursive();
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        assertFalse("It is not possible to delete build.", file.exists());
        assertEquals("Disk usage should have information about 2 builds.", 2, property.getDiskUsage().getBuildDiskUsage(false).size());
        j.jenkins.reload();
        project = (FreeStyleProject) j.jenkins.getItem(project.getDisplayName());
        property = project.getProperty(DiskUsageProperty.class);
        assertEquals("Deleted build without Jenkins should not be loaded.", 1, property.getDiskUsage().getBuildDiskUsage(false).size());
        
    }
    
    private TestThread runRemoveThread(ProjectDiskUsage usage){
        final ProjectDiskUsage diskUsage = usage;
        TestThread removeThread = new TestThread("removeFromSet"){
          public void run(){
              try{
                int count = 0;
                while(count < 1000){
                        count++;
                        diskUsage.removeBuild(diskUsage.getDiskUsageBuildInformation(count));
                    }
                } catch (ConcurrentModificationException ex) {
                    exception = ex;
                }catch (Exception ex){
                    exception = ex;
                }
          }
        };
        removeThread.start();
        return removeThread;
    }
    
    private TestThread runPutThread(ProjectDiskUsage usage){
        final ProjectDiskUsage diskUsage = usage;
        TestThread putThread = new TestThread("putIntoSet"){      
         public void run(){
                try {
                    int count = 0;
                    while(count < 1000){
                        count++;
                        GregorianCalendar calendar = new GregorianCalendar();
                        calendar.set(2014, 1, 1);
                        calendar.add(GregorianCalendar.MINUTE, count);
                        diskUsage.addBuildInformation(new DiskUsageBuildInformation(
                            Integer.toString(count),
                            calendar.getTimeInMillis(),
                            count,
                            0l), null);
                    }
                } catch (ConcurrentModificationException ex) {
                    exception = ex;
                }catch (Exception ex){
                    exception = ex;
                } 
          }  
        };
        putThread.start();
        return putThread;
    }
    
    private TestThread runSaveThread(ProjectDiskUsage usage){
        final ProjectDiskUsage diskUsage = usage;
        TestThread saveThread = new TestThread("saveSet"){
          public void run(){
              try{
                  int count = 0;
                  while(count<100){
                      count++;
                      diskUsage.save();
                  }
              } catch (ConcurrentModificationException ex) {
                    exception = ex;
                }catch (Exception ex){
                    exception = ex;
                } 
          }  
        };
        saveThread.start();
        return saveThread;
    }
    
    private void checkForConcurrencyException(Exception exception){
        exception.printStackTrace();
        if(exception instanceof ConcurrentModificationException){
            fail("DiskUsageProperty is not thread save. Attribute #diskUsageProperty caused ConcurrentModitifiactionException");
            return;
        }
        fail("Checking of thread safety caused Exception which is not connected with thread safety problem.");
    }
    
    //JENKINS-29143
    @Test
    public void testThreadSaveOperationUnderSetOfDiskUsageBuildInformation() throws Exception{
        final FreeStyleProject project = j.createFreeStyleProject();
        final ProjectDiskUsage diskUsage = new ProjectDiskUsage();
        diskUsage.setProject(project);
        TestThread putThread = runPutThread(diskUsage);
        TestThread removeThread = runRemoveThread(diskUsage);
        TestThread saveThread = runSaveThread(diskUsage);
        while(putThread.isAlive() || removeThread.isAlive()|| saveThread.isAlive()){
            Thread.currentThread().sleep(1000);
        }
        
        Exception ex = putThread.getException();
        if(putThread.getException()!=null){
            checkForConcurrencyException(ex);
        }
        ex = removeThread.getException();
        if(removeThread.getException()!=null){
            checkForConcurrencyException(ex);
        }
        ex = saveThread.getException();
        if(saveThread.getException()!=null){
            checkForConcurrencyException(ex);
        }
    }
    
    public class TestThread extends Thread {
       
        TestThread(String name){
            super(name);
        }
       
        public Exception exception;
        
        public Exception getException(){
            return exception;
        }
        
    }
    
    
    @Target(METHOD)
    @Retention(RUNTIME)
    @JenkinsRecipe(ReplaceHudsonHomeWithCurrentPath.CurrentWorkspacePath.class)
    public @interface ReplaceHudsonHomeWithCurrentPath {
        
        String value() default "";
        
        class CurrentWorkspacePath extends JenkinsRecipe.Runner<ReplaceHudsonHomeWithCurrentPath>{
            private String paths;
            public void decorateHome(JenkinsRule rule, File home){
                if(paths.isEmpty())
                    return;
                for(String path : paths.split(",")){
                    path = path.trim();
                    try {
                        File file = new File(home,path);
                        XmlFile xmlFile = new XmlFile(file);
                        String content = xmlFile.asString();
                        content = content.replace("${JENKINS_HOME}", home.getAbsolutePath());
                        PrintStream stream = new PrintStream(file);
                        stream.print(content);
                        stream.close();
                    } catch (IOException ex) {
                        Logger.getLogger(DiskUsagePropertyTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            
            @Override
            public void setup(JenkinsRule jenkinsRule, ReplaceHudsonHomeWithCurrentPath recipe) throws Exception {
                paths = recipe.value();
            }
        }
    }
    
    class WorkspacePathAnnotation implements Annotation{

        public Class<? extends Annotation> annotationType() {
            return WorkspacePathAnnotation.class;
        }
        
    }
    
    
    
}
