/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import hudson.plugins.disk_usage.*;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.FreeStyleProject;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author Lucie Votypkova
 */
public class WorkspaceDiskUsageCalculationThreadTest extends HudsonTestCase{
    private void waitUntilThreadEnds(WorkspaceDiskUsageCalculationThread calculation) throws InterruptedException{
        Thread thread = null;
        //wait until thread ends
        for(Thread t : Thread.getAllStackTraces().keySet()){
            if(calculation.name.equals(t.getName())){
                while(thread.isAlive())
                    Thread.sleep(100);
                break;
            }
        }
    }
    
     private List<File> readFileList(File file) throws FileNotFoundException, IOException{
        List<File> files = new ArrayList<File>();
        String path = file.getParentFile().getAbsolutePath();
        BufferedReader content = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String line = content.readLine();
        while(line!=null){
            files.add(new File(path + "/" + line));
            line = content.readLine();
        }
        return files;
    }
    
    private Long getSize(List<File> files){
        Long length = 0l;
        for(File file: files){

            length += file.length();
        }
        return length; 
    }
    
    private Slave createSlave(String name, String remoteFS) throws Exception{
        DumbSlave slave = new DumbSlave(name, "dummy",
            remoteFS, "2", Mode.NORMAL, "", createComputerLauncher(null),
            RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
    	hudson.addNode(slave);
        while(slave.toComputer()==null || !slave.toComputer().isOnline()){
            Thread.sleep(100);
        }
        return slave;
    }
    
    @Test
    @LocalData
    public void testExecute() throws IOException, InterruptedException, Exception{
        //turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        jenkins.getExtensionList(RunListener.class).remove(listener);
        Slave slave1 = createSlave("slave1", new File(hudson.getRootDir(),"workspace1").getPath());
        Slave slave2 = createSlave("slave2", new File(hudson.getRootDir(),"workspace2").getPath());
        FreeStyleProject project1 = createFreeStyleProject("project1");
        FreeStyleProject project2 = createFreeStyleProject("project2");
        project1.setAssignedNode(slave1);
        project2.setAssignedNode(slave1);
        buildAndAssertSuccess(project1);
        buildAndAssertSuccess(project2);
        project1.setAssignedNode(slave2);
        buildAndAssertSuccess(project1);
        File f = new File(slave1.getWorkspaceFor(project1).getRemote());
        File file = new File(slave1.getWorkspaceFor(project1).getRemote(), "fileList");
        File file2 = new File(slave2.getWorkspaceFor(project1).getRemote(), "fileList");
        Long size = getSize(readFileList(file)) + slave1.getWorkspaceFor(project1).length();
        size += getSize(readFileList(file2)) + slave2.getWorkspaceFor(project1).length();
        file = new File(slave1.getWorkspaceFor(project2).getRemote(), "fileList");
        Long size2 = getSize(readFileList(file)) + slave1.getWorkspaceFor(project2).length() + slave2.getWorkspaceFor(project2).length();      
        WorkspaceDiskUsageCalculationThread thread = new WorkspaceDiskUsageCalculationThread();
        thread.execute(TaskListener.NULL);
        waitUntilThreadEnds(thread);
        Assert.assertEquals("Calculation of job workspace disk usage does not return right size.", size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());       
        Assert.assertEquals("Calculation of job workspace disk usage does not return right size.", size2, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
    }
    
    @Test
    @LocalData
    public void testExecuteMatrixProject() throws Exception {
        //turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        jenkins.getExtensionList(RunListener.class).remove(listener);
        jenkins.setNumExecutors(0);
        Slave slave1 = createSlave("slave1", new File(hudson.getRootDir(),"workspace1").getPath());
        AxisList axes = new AxisList();
        TextAxis axis1 = new TextAxis("axis","axis1 axis2 axis3");
        axes.add(axis1);
        MatrixProject project1 = createMatrixProject("project1");
        project1.setAxes(axes);
        project1.setAssignedNode(slave1);
        buildAndAssertSuccess(project1);
        MatrixProject project2 = createMatrixProject("project2");
        AxisList axes2 = new AxisList();
        TextAxis axis2 = new TextAxis("axis","axis1 axis2");
        axes2.add(axis2);
        project2.setAxes(axes2);
        project2.setAssignedNode(slave1);
        buildAndAssertSuccess(project2);
        Slave slave2 = createSlave("slave2", new File(hudson.getRootDir(),"workspace2").getPath());
        slave1.toComputer().setTemporarilyOffline(true, null);
        project1.setAssignedNode(slave2);
        buildAndAssertSuccess(project1);
        WorkspaceDiskUsageCalculationThread thread = new WorkspaceDiskUsageCalculationThread();
        thread.execute(TaskListener.NULL);
        waitUntilThreadEnds(thread);
        slave1.toComputer().setTemporarilyOffline(false, null);
        //project 1
        File file = new File(slave1.getWorkspaceFor(project1).getRemote(), "fileList");
        File fileAxis1 = new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis1", "fileList");
        File fileAxis2 = new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis2", "fileList");
        File fileAxis3 = new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis3", "fileList");
        Long size = getSize(readFileList(file)) + slave1.getWorkspaceFor(project1).length();
        Long sizeAxis1 = getSize(readFileList(fileAxis1)) + new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis1").length();
        Long sizeAxis2 = getSize(readFileList(fileAxis2)) + new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis2").length();
        Long sizeAxis3 = getSize(readFileList(fileAxis3)) + new File(slave1.getWorkspaceFor(project1).getRemote()+"/axis/axis3").length();
        file = new File(slave2.getWorkspaceFor(project1).getRemote(), "fileList");
        fileAxis1 = new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis1", "fileList");
        fileAxis2 = new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis2", "fileList");
        fileAxis3 = new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis3", "fileList");
        size += getSize(readFileList(file)) + slave2.getWorkspaceFor(project1).length();
        sizeAxis1 += getSize(readFileList(fileAxis1)) + new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis1").length();
        sizeAxis2 += getSize(readFileList(fileAxis2)) + new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis2").length();
        sizeAxis3 += getSize(readFileList(fileAxis3)) + new File(slave2.getWorkspaceFor(project1).getRemote()+"/axis/axis3").length();
        Assert.assertEquals("Calculation of matrix job workspace disk usage does not return right size.", size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        //configurations
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis1, project1.getItem("axis=axis1").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis2, project1.getItem("axis=axis2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis3, project1.getItem("axis=axis3").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        //project 2
        file = new File(slave1.getWorkspaceFor(project2).getRemote(), "fileList");
        fileAxis1 = new File(slave1.getWorkspaceFor(project2).getRemote()+"/axis/axis1", "fileList");
        fileAxis2 = new File(slave1.getWorkspaceFor(project2).getRemote()+"/axis/axis2", "fileList");
        size = getSize(readFileList(file)) + slave1.getWorkspaceFor(project2).length();
        sizeAxis1 = getSize(readFileList(fileAxis1)) + new File(slave1.getWorkspaceFor(project2).getRemote()+"/axis/axis1").length();
        sizeAxis2 = getSize(readFileList(fileAxis2)) + new File(slave1.getWorkspaceFor(project2).getRemote()+"/axis/axis2").length();
        Assert.assertEquals("Calculation of matrix job workspace disk usage does not return right size.", size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        //configurations
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis1, project2.getItem("axis=axis1").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
        Assert.assertEquals("Calculation of matrix configuration workspace disk usage does not return right size.", sizeAxis2, project2.getItem("axis=axis2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace());
       
    }
}
