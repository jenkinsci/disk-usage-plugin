package hudson.plugins.disk_usage.integration;

import hudson.plugins.disk_usage.*;
import java.io.File;
import java.io.IOException;
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
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

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
        DiskUsageProperty projectProperty = new DiskUsageProperty();
        projectProperty.setDiskUsageWithoutBuilds(sizeOfProject);
        project.addProperty(projectProperty);
        DiskUsageProperty matrixProjectProperty = new DiskUsageProperty();
        matrixProjectProperty.setDiskUsageWithoutBuilds(sizeOfMatrixProject);
        matrixProject.addProperty(matrixProjectProperty);
        long size1 = 5390;
        int count = 1;
        Long matrixProjectTotalSize = sizeOfMatrixProject;
        for(MatrixConfiguration c: matrixProject.getItems()){
            DiskUsageProperty configurationProperty = new DiskUsageProperty();
            configurationProperty.setDiskUsageWithoutBuilds(count*size1);
            c.addProperty(configurationProperty);  
            matrixProjectTotalSize += count*size1;
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
        j.jenkins.getPlugin(DiskUsagePlugin.class).setCheckWorkspaceOnSlave(true);
        prop.checkWorkspaces();
        assertTrue("DiskUsage property should contains slave " + slave2.getDisplayName() + " in slaveWorkspaceUsage.", nodes.contains(slave2.getNodeName()));
        assertTrue("DiskUsage property should contains slave " + slave1.getDisplayName() + " in slaveWorkspaceUsage when detection of user workspace withour reference from project is set.", nodes.contains(slave1.getNodeName()));      
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
        Slave slave1 = DiskUsageTestUtil.createSlave("slave1", new File(j.jenkins.getRootDir(),"workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        Slave slave2 = DiskUsageTestUtil.createSlave("slave2", new File(j.jenkins.getRootDir(),"workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        property.putSlaveWorkspaceSize(j.jenkins, j.jenkins.getRawWorkspaceDir(), 10495l);
        property.putSlaveWorkspaceSize(slave1,slave1.getRemoteFS(),5670l);
        property.putSlaveWorkspaceSize(slave2, slave2.getRemoteFS(), 7987l);
        j.jenkins.removeNode(slave2);
        property.checkWorkspaces();
        assertFalse("Disk usage property should not contains slave which does not exist.", property.getSlaveWorkspaceUsage().containsKey(slave2.getNodeName()));
        assertTrue("Disk usage property should not slave1.", property.getSlaveWorkspaceUsage().containsKey(slave1.getNodeName()));
        assertTrue("Disk usage property should contains jenkins master.", property.getSlaveWorkspaceUsage().containsKey(j.jenkins.getNodeName()));
    }   
}
