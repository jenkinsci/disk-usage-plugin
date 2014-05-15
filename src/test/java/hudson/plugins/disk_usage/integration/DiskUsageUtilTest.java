package hudson.plugins.disk_usage.integration;

import hudson.plugins.disk_usage.*;
import hudson.matrix.AxisList;
import hudson.matrix.LabelAxis;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import java.util.ArrayList;
import hudson.model.AbstractBuild;
import java.io.File;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.model.TopLevelItem;
import hudson.model.listeners.RunListener;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageUtilTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    @LocalData
    public void testCalculateDiskUsageForBuild() throws Exception{
        FreeStyleProject project = (FreeStyleProject) j.jenkins.getItem("project1");
        AbstractBuild build = project.getBuildByNumber(2);
        File file = new File(build.getRootDir(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + build.getRootDir().length();
        DiskUsageUtil.calculateDiskUsageForBuild(build.getId(), project);
        Assert.assertEquals("Calculation of build disk usage does not return right size of build directory.", size, DiskUsageTestUtil.getBuildDiskUsageAction(build).getDiskUsage());
    }
    
    @Test
    @LocalData
    public void testCalculateDiskUsageForMatrixBuild() throws Exception{
        MatrixProject project = (MatrixProject) j.jenkins.getItem("project1");
        AbstractBuild build = project.getBuildByNumber(1);
        File file = new File(build.getRootDir(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + build.getRootDir().length();
        Long sizeAll = size;
        for(MatrixConfiguration config: project.getActiveConfigurations()){
            AbstractBuild b = config.getBuildByNumber(1);
            File f = new File(b.getRootDir(), "fileList");
            sizeAll += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(f)) + build.getRootDir().length();
        }
        DiskUsageUtil.calculateDiskUsageForBuild(build.getId(), project);
        Assert.assertEquals("Matrix project project1 has disk usage size.", size, DiskUsageTestUtil.getBuildDiskUsageAction(build).getDiskUsage());
        for(MatrixConfiguration config: project.getActiveConfigurations()){
            DiskUsageUtil.calculateDiskUsageForBuild(config.getBuildByNumber(1).getId(), config);
        }
        Assert.assertEquals("Matrix project project1 has wrong size for its build.", sizeAll, DiskUsageTestUtil.getBuildDiskUsageAction(build).getAllDiskUsage());
    }
    
    @Test
    @LocalData
    public void testCalculateDiskUsageForJob() throws Exception{
        FreeStyleProject project = (FreeStyleProject) j.jenkins.getItem("project1");
        File file = new File(project.getRootDir(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + project.getRootDir().length();
        size += project.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        DiskUsageUtil.calculateDiskUsageForProject(project);
        Assert.assertEquals("Calculation of job disk usage does not return right size of job without builds.", size, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds());
        
    }
    
    @Test
    @LocalData
    public void testCalculateDiskUsageForMatrixJob() throws Exception{
        MatrixProject project = (MatrixProject) j.jenkins.getItem("project1");
        File file = new File(project.getRootDir(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + project.getRootDir().length();
        size += project.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        Long sizeAll = size;
        for(MatrixConfiguration config: project.getItems()){
            File f = new File(config.getRootDir(), "fileList");
            sizeAll += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(f)) + config.getRootDir().length();
            sizeAll += config.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        }
        DiskUsageUtil.calculateDiskUsageForProject(project);
        Assert.assertEquals("Calculation of job disk usage does not return right size of job without builds.", size, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds());
        for(AbstractProject p: project.getItems()){
            DiskUsageUtil.calculateDiskUsageForProject(p);
        }
        Assert.assertEquals("Calculation of job disk usage does not return right size of job and its sub-jobs without builds.", sizeAll, project.getAction(ProjectDiskUsageAction.class).getAllDiskUsageWithoutBuilds());
    
    }
    
    @Test
    @LocalData
    public void testCalculateDiskUsageWorkspaceForProject() throws Exception{
         //turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Slave slave1 = DiskUsageTestUtil.createSlave("slave1", new File(j.jenkins.getRootDir(),"workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        Slave slave2 = DiskUsageTestUtil.createSlave("slave2", new File(j.jenkins.getRootDir(),"workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        FreeStyleProject project1 = j.createFreeStyleProject("project1");
        FreeStyleProject project2 = j.createFreeStyleProject("project2");
        project1.setAssignedNode(slave1);
        project2.setAssignedNode(slave1);
        j.buildAndAssertSuccess(project1);
        j.buildAndAssertSuccess(project2);
        project1.setAssignedNode(slave2);
        project2.setAssignedNode(slave2);
        j.buildAndAssertSuccess(project1);
        j.buildAndAssertSuccess(project2);
        File file = new File(slave1.getWorkspaceFor(project1).getRemote(), "fileList");
        File file2 = new File(slave2.getWorkspaceFor(project1).getRemote(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + slave1.getWorkspaceFor(project1).length();
        size += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file2)) + slave2.getWorkspaceFor(project1).length();
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        Assert.assertEquals("Calculation of job workspace disk usage does not return right size.", size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        file = new File(slave1.getWorkspaceFor(project2).getRemote(), "fileList");
        size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + slave1.getWorkspaceFor(project2).length() + slave2.getWorkspaceFor(project2).length();
        DiskUsageUtil.calculateWorkspaceDiskUsage(project2);
        Assert.assertEquals("Calculation of job workspace disk usage does not return right size.", size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
    }

    @Test
    @LocalData
    public void testCalculateDiskUsageWorkspaceForMatrixProjectWithConfigurationInSameDirectory() throws Exception{
         //turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        j.jenkins.setNumExecutors(0);
        Slave slave1 = DiskUsageTestUtil.createSlave("slave1", new File(j.jenkins.getRootDir(),"workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        AxisList axes = new AxisList();
        TextAxis axis1 = new TextAxis("axis","axis1 axis2 axis3");
        axes.add(axis1);
        MatrixProject project1 = j.createMatrixProject("project1");
        project1.setAxes(axes);
        project1.setAssignedNode(slave1);
        j.buildAndAssertSuccess(project1);
        Slave slave2 = DiskUsageTestUtil.createSlave("slave2", new File(j.jenkins.getRootDir(),"workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        ArrayList<String> slaves = new ArrayList<String>();
        slaves.add("slave2");
        LabelAxis axis2 = new LabelAxis("label",slaves);
        axes.add(axis2);
        project1.setAxes(axes);
        File file = new File(slave1.getWorkspaceFor(project1).getRemote(), "fileList");
        File fileAxis1 = new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis1", "fileList");
        File fileAxis2 = new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis2", "fileList");
        File fileAxis3 = new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis3", "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + slave1.getWorkspaceFor(project1).length();
        Long sizeAxis1 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis1)) + new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis1").length();
        Long sizeAxis2 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis2)) + new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis2").length();
        Long sizeAxis3 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis3)) + new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis3").length();
        for(MatrixConfiguration c: project1.getItems()){
            DiskUsageUtil.calculateWorkspaceDiskUsage(c);
        }
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        Assert.assertEquals("Calculation of matrix job workspace disk usage does not return right size.", size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis1, project1.getItem("axis=axis1").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis2, project1.getItem("axis=axis2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis3, project1.getItem("axis=axis3").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        
        
        //next build - configuration are builded on next slave
        //test if not active configuration are find and right counted
        // test if works with more complex configurations
        j.buildAndAssertSuccess(project1);
        for(MatrixConfiguration c: project1.getItems()){
            DiskUsageUtil.calculateWorkspaceDiskUsage(c);
        }
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis1, project1.getItem("axis=axis1").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis2, project1.getItem("axis=axis2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis3, project1.getItem("axis=axis3").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        fileAxis1 = new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis1/label/slave2", "fileList");
        fileAxis2 = new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis2/label/slave2", "fileList");
        fileAxis3 = new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis3/label/slave2", "fileList");      
        sizeAxis1 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis1)) + new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis1/label/slave2").length();
        sizeAxis2 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis2)) + new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis2/label/slave2").length();
        sizeAxis3 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis3)) + new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis3/label/slave2").length();
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis1, project1.getItem("axis=axis1,label=slave2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis2, project1.getItem("axis=axis2,label=slave2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis3, project1.getItem("axis=axis3,label=slave2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        
        
        //matrix project is builded on the next slave
        //test if new folder on slave2 is counted too
        project1.setAssignedNode(slave2);
        j.buildAndAssertSuccess(project1);
        file = new File(slave2.getWorkspaceFor(project1).getRemote(), "fileList");
        size += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + slave2.getWorkspaceFor(project1).length();
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        Assert.assertEquals("Calculation of matrix job workspace disk usage does not return right size.", size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
    }
    
    @Test
    @LocalData
    public void testCalculateDiskUsageWorkspaceWhenReferenceFromJobDoesNotExists() throws Exception{
         //turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        DiskUsagePlugin plugin = j.jenkins.getPlugin(DiskUsagePlugin.class);
        plugin.getConfiguration().setCheckWorkspaceOnSlave(true); 
        j.jenkins.setNumExecutors(0);
        Slave slave1 = DiskUsageTestUtil.createSlave("slave1", new File(j.jenkins.getRootDir(),"workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        AxisList axes = new AxisList();
        TextAxis axis1 = new TextAxis("axis","axis1 axis2 axis3");
        axes.add(axis1);
        MatrixProject project1 = j.createMatrixProject("project1");
        project1.setAxes(axes);
        project1.setAssignedNode(slave1);
        j.buildAndAssertSuccess(project1);
        Slave slave2 = DiskUsageTestUtil.createSlave("slave2", new File(j.jenkins.getRootDir(),"workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        File file = new File(slave1.getWorkspaceFor(project1).getRemote(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + slave1.getWorkspaceFor(project1).length();
        File fileAxis1 = new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis1/label/slave2", "fileList");
        File fileAxis2 = new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis2/label/slave2", "fileList");
        File fileAxis3 = new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis3/label/slave2", "fileList");      
        Long sizeAxis1 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis1)) + new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis1/label/slave2").length();
        Long sizeAxis2 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis2)) + new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis2/label/slave2").length();
        Long sizeAxis3 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis3)) + new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis3/label/slave2").length();
        file = new File(slave2.getWorkspaceFor(project1).getRemote(), "fileList");
        size += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + slave2.getWorkspaceFor(project1).length() + sizeAxis1 + sizeAxis2 + sizeAxis3;
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        Assert.assertEquals("Calculation of matrix job workspace disk usage does not return right size.", size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());      
        plugin.getConfiguration().setCheckWorkspaceOnSlave(false);
    }
    
    
    @Test
    public void testCalculateDiskUsageWorkspaceUpdateIformationIfSavedWorkspaceDoesNotExists() throws Exception{
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Slave slave1 = DiskUsageTestUtil.createSlave("slave1", new File(j.jenkins.getRootDir(),"workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        Slave slave2 = DiskUsageTestUtil.createSlave("slave2", new File(j.jenkins.getRootDir(),"workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        FreeStyleProject project1 = j.createFreeStyleProject("project1");
        project1.setAssignedNode(slave1);
        j.buildAndAssertSuccess(project1);
        
        DiskUsageProperty prop = project1.getProperty(DiskUsageProperty.class);
        if(prop == null){
            prop = new DiskUsageProperty();
            project1.addProperty(prop);
        }
        prop.putSlaveWorkspaceSize(slave2, slave2.getWorkspaceFor((TopLevelItem)project1).getRemote(), 54356l);
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        assertFalse("Slave slave2 should be removed from disk usage, because a workspace for project1 does not exist on this slave.",prop.getSlaveWorkspaceUsage().containsKey(slave2.getNodeName()));
        assertTrue("Disk usage should contains slave1, there is a workspace for project1.", prop.getSlaveWorkspaceUsage().containsKey(slave1.getNodeName()));
    }
    
    @Test
    public void testParseExcludedJobsFromString() throws Exception{
        FreeStyleProject projectWithSpace = j.createFreeStyleProject("Project with space");
        FreeStyleProject project = j.createFreeStyleProject("Project");
        FreeStyleProject project2 = j.createFreeStyleProject("Project2");
        FreeStyleProject projectWithSpace2 = j.createFreeStyleProject(" Project with space");
        String excluded = "Project with space,Project";
        List<String> excludedJobs = DiskUsageUtil.parseExcludedJobsFromString(excluded);
        assertTrue("Excluded jobs should contains job without spaces in name", excludedJobs.contains(project.getName()));
        assertTrue("Excluded jobs should contains job with spaces in name", excludedJobs.contains(projectWithSpace.getName()));
        excluded = "Project with space, Project";
        excludedJobs = DiskUsageUtil.parseExcludedJobsFromString(excluded);
        assertTrue("Excluded jobs should parse jobs with spaces even if the space is used as separator.", excludedJobs.contains(projectWithSpace.getName()));
        assertFalse("Excluded jobs should parse jobs correctly even if the space is used as separator.", excludedJobs.contains(projectWithSpace2.getName()));
        assertFalse("Excluded jobs should not contains jobs which is not occuren in excluded string.", excludedJobs.contains(project2.getName()));
        excluded = "Project with space, Project5";
        excludedJobs = DiskUsageUtil.parseExcludedJobsFromString(excluded);
        assertFalse("Excluded jobs should not contains jobs which does not exists.", excludedJobs.contains("Project5"));
        excluded = "Project with space, ";
        assertTrue("Excluded jobs should be parsed correctly even if there additional separator", excludedJobs.contains(projectWithSpace.getName()) && excludedJobs.size()==1);
    }
   
}
