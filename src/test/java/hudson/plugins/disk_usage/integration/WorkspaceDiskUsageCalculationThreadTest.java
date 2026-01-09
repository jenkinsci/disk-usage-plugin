/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.disk_usage.BuildDiskUsageCalculationThread;
import hudson.plugins.disk_usage.DiskUsageBuildListener;
import hudson.plugins.disk_usage.DiskUsageProjectActionFactory;
import hudson.plugins.disk_usage.DiskUsageProperty;
import hudson.plugins.disk_usage.ProjectDiskUsageAction;
import hudson.plugins.disk_usage.WorkspaceDiskUsageCalculationThread;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
public class WorkspaceDiskUsageCalculationThreadTest {

    private void waitUntilThreadEnds(WorkspaceDiskUsageCalculationThread calculation) throws InterruptedException {
        Thread thread = null;
        // wait until thread ends
        for(Thread t: Thread.getAllStackTraces().keySet()) {
            if(calculation.name.equals(t.getName())) {
                while(thread.isAlive()) {
                    Thread.sleep(100);
                }
                break;
            }
        }
    }

    private List<File> readFileList(File file) throws FileNotFoundException, IOException {
        List<File> files = new ArrayList<>();
        String path = file.getParentFile().getAbsolutePath();
        BufferedReader content = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String line = content.readLine();
        while(line != null) {
            files.add(new File(path + "/" + line));
            line = content.readLine();
        }
        return files;
    }

    private Long getSize(List<File> files) {
        long length = 0L;
        for(File file: files) {

            length += file.length();
        }
        return length;
    }

    private Slave createAgent(JenkinsRule j, String name, String remoteFS) throws Exception {
        DumbSlave agent = new DumbSlave(name, "dummy",
                                        remoteFS, "2", Mode.NORMAL, "", j.createComputerLauncher(null),
                                        RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        j.getInstance().addNode(agent);
        while(agent.toComputer() == null || !agent.toComputer().isOnline()) {
            Thread.sleep(100);
        }
        return agent;
    }

    @Test
    @LocalData
    void testExecute(JenkinsRule j) throws IOException, InterruptedException, Exception {
        // turn off run listener
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.getInstance().getExtensionList(RunListener.class).remove(listener);
        Slave agent1 = createAgent(j, "agent1", new File(j.getInstance().getRootDir(), "workspace1").getPath());
        Slave agent2 = createAgent(j, "agent2", new File(j.getInstance().getRootDir(), "workspace2").getPath());
        FreeStyleProject project1 = j.createFreeStyleProject("project1");
        FreeStyleProject project2 = j.createFreeStyleProject("project2");
        project1.setAssignedNode(agent1);
        project2.setAssignedNode(agent1);
        j.buildAndAssertSuccess(project1);
        j.buildAndAssertSuccess(project2);
        project1.setAssignedNode(agent2);
        j.buildAndAssertSuccess(project1);
        File f = new File(agent1.getWorkspaceFor(project1).getRemote());
        File file = new File(agent1.getWorkspaceFor(project1).getRemote(), "fileList");
        File file2 = new File(agent2.getWorkspaceFor(project1).getRemote(), "fileList");
        Long size = getSize(readFileList(file)) + agent1.getWorkspaceFor(project1).length();
        size += getSize(readFileList(file2)) + agent2.getWorkspaceFor(project1).length();
        file = new File(agent1.getWorkspaceFor(project2).getRemote(), "fileList");
        Long size2 = getSize(readFileList(file)) + agent1.getWorkspaceFor(project2).length() + agent2.getWorkspaceFor(project2).length();
        WorkspaceDiskUsageCalculationThread thread = new WorkspaceDiskUsageCalculationThread();
        if(thread.isExecuting()) {
            waitUntilThreadEnds(thread);
        }
        thread.execute(TaskListener.NULL);
        waitUntilThreadEnds(thread);
        assertEquals(size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of job workspace disk usage does not return right size.");
        assertEquals(size2, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of job workspace disk usage does not return right size.");
    }

    @Test
    @LocalData
    void testExecuteMatrixProject(JenkinsRule j) throws Exception {
        // turn off run listener
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.getInstance().getExtensionList(RunListener.class).remove(listener);
        j.getInstance().setNumExecutors(0);
        Slave agent1 = createAgent(j, "agent1", new File(j.getInstance().getRootDir(), "workspace1").getPath());
        AxisList axes = new AxisList();
        TextAxis axis1 = new TextAxis("axis", "axis1 axis2 axis3");
        axes.add(axis1);
        MatrixProject project1 = j.jenkins.createProject(MatrixProject.class, "project1");
        project1.setAxes(axes);
        project1.setAssignedNode(agent1);
        j.buildAndAssertSuccess(project1);
        MatrixProject project2 = j.jenkins.createProject(MatrixProject.class, "project2");
        AxisList axes2 = new AxisList();
        TextAxis axis2 = new TextAxis("axis", "axis1 axis2");
        axes2.add(axis2);
        project2.setAxes(axes2);
        project2.setAssignedNode(agent1);
        j.buildAndAssertSuccess(project2);
        Slave agent2 = createAgent(j, "agent2", new File(j.getInstance().getRootDir(), "workspace2").getPath());
        agent1.toComputer().setTemporarilyOffline(true, null);
        project1.setAssignedNode(agent2);
        j.buildAndAssertSuccess(project1);
        WorkspaceDiskUsageCalculationThread thread = new WorkspaceDiskUsageCalculationThread();
        if(thread.isExecuting()) {
            waitUntilThreadEnds(thread);
        }
        thread.execute(TaskListener.NULL);
        waitUntilThreadEnds(thread);
        agent1.toComputer().setTemporarilyOffline(false, null);
        // project 1
        File file = new File(agent1.getWorkspaceFor(project1).getRemote(), "fileList");
        File fileAxis1 = new File(agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis1", "fileList");
        File fileAxis2 = new File(agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis2", "fileList");
        File fileAxis3 = new File(agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis3", "fileList");
        Long size = getSize(readFileList(file)) + agent1.getWorkspaceFor(project1).length();
        Long sizeAxis1 = getSize(readFileList(fileAxis1)) + new File(
            agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis1").length();
        Long sizeAxis2 = getSize(readFileList(fileAxis2)) + new File(
            agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis2").length();
        Long sizeAxis3 = getSize(readFileList(fileAxis3)) + new File(
            agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis3").length();
        file = new File(agent2.getWorkspaceFor(project1).getRemote(), "fileList");
        fileAxis1 = new File(agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis1", "fileList");
        fileAxis2 = new File(agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis2", "fileList");
        fileAxis3 = new File(agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis3", "fileList");
        size += getSize(readFileList(file)) + agent2.getWorkspaceFor(project1).length();
        sizeAxis1 += getSize(readFileList(fileAxis1)) + new File(
            agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis1").length();
        sizeAxis2 += getSize(readFileList(fileAxis2)) + new File(
            agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis2").length();
        sizeAxis3 += getSize(readFileList(fileAxis3)) + new File(
            agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis3").length();
        assertEquals(size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix job workspace disk usage does not return right size.");
        // configurations
        assertEquals(sizeAxis1, project1.getItem("axis=axis1").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        assertEquals(sizeAxis2, project1.getItem("axis=axis2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        assertEquals(sizeAxis3, project1.getItem("axis=axis3").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        // project 2
        file = new File(agent1.getWorkspaceFor(project2).getRemote(), "fileList");
        fileAxis1 = new File(agent1.getWorkspaceFor(project2).getRemote() + "/axis/axis1", "fileList");
        fileAxis2 = new File(agent1.getWorkspaceFor(project2).getRemote() + "/axis/axis2", "fileList");
        size = getSize(readFileList(file)) + agent1.getWorkspaceFor(project2).length();
        sizeAxis1 = getSize(readFileList(fileAxis1)) + new File(
            agent1.getWorkspaceFor(project2).getRemote() + "/axis/axis1").length();
        sizeAxis2 = getSize(readFileList(fileAxis2)) + new File(
            agent1.getWorkspaceFor(project2).getRemote() + "/axis/axis2").length();
        assertEquals(size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix job workspace disk usage does not return right size.");
        // configurations
        assertEquals(sizeAxis1, project2.getItem("axis=axis1").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        assertEquals(sizeAxis2, project2.getItem("axis=axis2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");

    }

    @Test
    void testDoNotCalculateUnenabledDiskUsage(JenkinsRule j) throws Exception {
        FreeStyleProject projectWithoutDiskUsage = j.getInstance().createProject(FreeStyleProject.class, "projectWithoutDiskUsage");
        FreeStyleBuild build = projectWithoutDiskUsage.createExecutable();
        DiskUsageProjectActionFactory.DESCRIPTOR.disableWorkspacesDiskUsageCalculation();
        BuildDiskUsageCalculationThread calculation = AperiodicWork.all().get(BuildDiskUsageCalculationThread.class);
        calculation.execute(TaskListener.NULL);
        assertEquals(0, projectWithoutDiskUsage.getProperty(DiskUsageProperty.class).getAllWorkspaceSize(), 0, "Disk usage for build should not be counted.");
        DiskUsageProjectActionFactory.DESCRIPTOR.enableWorkspacesDiskUsageCalculation();
    }

    @Test
    @LocalData
    void testDoNotExecuteDiskUsageWhenPreviousCalculationIsInProgress(JenkinsRule j) throws Exception {
        WorkspaceDiskUsageCalculationThread testCalculation = new WorkspaceDiskUsageCalculationThread();
        DiskUsageTestUtil.cancelCalculation(testCalculation);
        FreeStyleProject project = j.getInstance().createProject(FreeStyleProject.class, "project1");
        TestDiskUsageProperty prop = new TestDiskUsageProperty();
        project.addProperty(prop);
        Slave agent1 = createAgent(j, "agent1", new File(j.getInstance().getRootDir(), "workspace1").getPath());
        prop.putAgentWorkspace(agent1, agent1.getWorkspaceFor(project).getRemote());
        Thread t = new Thread(testCalculation.getThreadName()){

            @Override
            public void run() {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(JobDiskUsageCalculationThreadTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        t.start();
        Thread.sleep(1000);
        testCalculation.doRun();
        assertEquals(0, project.getProperty(DiskUsageProperty.class).getAllWorkspaceSize(), 0, "Disk usage should not start calculation if preview calculation is in progress.");
        t.interrupt();
    }

    @Test
    @LocalData
    void testDoNotBreakLazyLoading(JenkinsRule j) throws Exception {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.getInstance().getItem("project1");
        project.isBuilding();
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        assertTrue(8 > loadedBuilds, "This test does not have sense if there is loaded all builds");
        WorkspaceDiskUsageCalculationThread calculation = AperiodicWork.all().get(WorkspaceDiskUsageCalculationThread.class);
        calculation.execute(TaskListener.NULL);
        assertTrue(project._getRuns().getLoadedBuilds().size() <= loadedBuilds, "WorkspaceCalculation should not cause loading of builds (only if the plugin is used for first time).");

    }

    @Test
    @LocalData
    void testDoNotCalculateExcludedJobs(JenkinsRule j) throws Exception {
        List<String> excludes = new ArrayList<>();
        excludes.add("excludedJob");
        DiskUsageProjectActionFactory.DESCRIPTOR.setExcludedJobs(excludes);
        DiskUsageProjectActionFactory.DESCRIPTOR.enableWorkspacesDiskUsageCalculation();
        FreeStyleProject excludedJob = j.getInstance().createProject(FreeStyleProject.class, "excludedJob");
        FreeStyleProject includedJob = j.getInstance().createProject(FreeStyleProject.class, "includedJob");
        if(Functions.isWindows()) {
            excludedJob.getBuildersList().add(new BatchFile("echo ahoj > log.log"));
            includedJob.getBuildersList().add(new BatchFile("echo ahoj > log.log"));
        } else {
            excludedJob.getBuildersList().add(new Shell("echo ahoj > log.log"));
            includedJob.getBuildersList().add(new Shell("echo ahoj > log.log"));
        }
        Slave agent1 = DiskUsageTestUtil.createAgent("agent1", new File(j.getInstance().getRootDir(), "workspace1").getPath(), j.getInstance(), j.createComputerLauncher(null));
        excludedJob.setAssignedLabel(agent1.getSelfLabel());
        includedJob.setAssignedLabel(agent1.getSelfLabel());
        j.buildAndAssertSuccess(excludedJob);
        j.buildAndAssertSuccess(includedJob);
        WorkspaceDiskUsageCalculationThread calculation = AperiodicWork.all().get(WorkspaceDiskUsageCalculationThread.class);
        calculation.execute(TaskListener.NULL);
        assertEquals(0, excludedJob.getProperty(DiskUsageProperty.class).getAllWorkspaceSize(), 0, "Disk usage for excluded project should not be counted.");
        assertTrue(includedJob.getProperty(DiskUsageProperty.class).getAllWorkspaceSize() > 0, "Disk usage for included project should be counted.");
        excludes.clear();
    }

    @Test
    @LocalData
    void testDoNotCountSizeTheSameWorkspaceTwice(JenkinsRule j) throws Exception {
        FreeStyleProject job = j.getInstance().createProject(FreeStyleProject.class, "project1");
        Slave agent1 = DiskUsageTestUtil.createAgent("agent1", new File(j.getInstance().getRootDir(), "workspace1").getPath(), j.getInstance(), j.createComputerLauncher(null));
        job.setAssignedLabel(agent1.getSelfLabel());
        j.buildAndAssertSuccess(job);
        j.buildAndAssertSuccess(job);
        j.buildAndAssertSuccess(job);
        File file = new File(agent1.getWorkspaceFor(job).getRemote(), "fileList");
        long size = getSize(readFileList(file)) + agent1.getWorkspaceFor(job).length();
        WorkspaceDiskUsageCalculationThread calculation = AperiodicWork.all().get(WorkspaceDiskUsageCalculationThread.class);
        calculation.execute(TaskListener.NULL);
        assertFalse(size > job.getAction(ProjectDiskUsageAction.class).getAllAgentWorkspaces(), "Disk usage should be counted correctly even for one workspace.");
        assertEquals(size, job.getAction(ProjectDiskUsageAction.class).getAllAgentWorkspaces(), 0, "Disk usage should be counted only one times for the same workspace.");
    }

    @TestExtension
    public static class TestDiskUsageProperty extends DiskUsageProperty {

        @Override
        public void putAgentWorkspaceSize(@NonNull Node node, String path, Long size) {
            LOGGER.fine("workspace size " + size);
            try {
                Thread.sleep(10000); // make this operation longer
            } catch (InterruptedException ex) {
                Logger.getLogger(WorkspaceDiskUsageCalculationThreadTest.class.getName()).log(Level.SEVERE, null, ex);
            }
            Map<String, Long> workspacesInfo = getAgentWorkspaceUsage().get(node.getNodeName());
            if(workspacesInfo == null) {
                workspacesInfo = new ConcurrentHashMap<>();
            }
            workspacesInfo.put(path, size);
            getAgentWorkspaceUsage().put(node.getNodeName(), workspacesInfo);
            saveDiskUsage();
        }
    }
}
