package hudson.plugins.disk_usage.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.matrix.AxisList;
import hudson.matrix.LabelAxis;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.model.TopLevelItem;
import hudson.model.listeners.RunListener;
import hudson.plugins.disk_usage.DiskUsageBuildListener;
import hudson.plugins.disk_usage.DiskUsagePlugin;
import hudson.plugins.disk_usage.DiskUsageProperty;
import hudson.plugins.disk_usage.DiskUsageUtil;
import hudson.plugins.disk_usage.ProjectDiskUsageAction;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
public class DiskUsageUtilTest {

    @Test
    @LocalData
    void testCalculateDiskUsageForBuild(JenkinsRule j) throws Exception {
        FreeStyleProject project = (FreeStyleProject) j.jenkins.getItem("project1");
        AbstractBuild<?,?> build = project.getBuildByNumber(2);
        File file = new File(build.getRootDir(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + build.getRootDir().length();
        DiskUsageUtil.calculateDiskUsageForBuild(build.getId(), project);
        Assertions.assertEquals(size, DiskUsageTestUtil.getBuildDiskUsageAction(build).getDiskUsage(), "Calculation of build disk usage does not return right size of build directory.");
    }

    @Test
    @LocalData
    void testCalculateDiskUsageForMatrixBuild(JenkinsRule j) throws Exception {
        MatrixProject project = (MatrixProject) j.jenkins.getItem("project1");
        AbstractBuild<?,?> build = project.getBuildByNumber(1);
        File file = new File(build.getRootDir(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + build.getRootDir().length();
        Long sizeAll = size;
        for(MatrixConfiguration config: project.getActiveConfigurations()) {
            AbstractBuild<?,?> b = config.getBuildByNumber(1);
            File f = new File(b.getRootDir(), "fileList");
            sizeAll += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(f)) + b.getRootDir().length();
        }
        DiskUsageUtil.calculateDiskUsageForBuild(build.getId(), project);
        Assertions.assertEquals(size, DiskUsageTestUtil.getBuildDiskUsageAction(build).getDiskUsage(), "Matrix project project1 has disk usage size.");
        for(MatrixConfiguration config: project.getActiveConfigurations()) {
            DiskUsageUtil.calculateDiskUsageForBuild(config.getBuildByNumber(1).getId(), config);
        }
        Assertions.assertEquals(sizeAll, DiskUsageTestUtil.getBuildDiskUsageAction(build).getAllDiskUsage(), "Matrix project project1 has wrong size for its build.");
    }

    @Test
    @LocalData
    void testCalculateDiskUsageForJob(JenkinsRule j) throws Exception {
        FreeStyleProject project = (FreeStyleProject) j.jenkins.getItem("project1");
        // all builds has to be loaded
        project.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        File file = new File(project.getRootDir(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + project.getRootDir().length();
        size += project.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        DiskUsageUtil.calculateDiskUsageForProject(project);
        Assertions.assertEquals(size, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), "Calculation of job disk usage does not return right size of job without builds.");

    }

    @Test
    @LocalData
    void testCalculateDiskUsageForMatrixJob(JenkinsRule j) throws Exception {
        MatrixProject project = (MatrixProject) j.jenkins.getItem("project1");
        // all builds has to be loaded
        project.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        File file = new File(project.getRootDir(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + project.getRootDir().length();
        size += project.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        Long sizeAll = size;
        for(MatrixConfiguration config: project.getItems()) {
            config.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
            File f = new File(config.getRootDir(), "fileList");
            sizeAll += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(f)) + config.getRootDir().length();
            sizeAll += config.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        }
        DiskUsageUtil.calculateDiskUsageForProject(project);
        Assertions.assertEquals(size, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), "Calculation of job disk usage does not return right size of job without builds.");
        for(AbstractProject<?,?> p: project.getItems()) {
            DiskUsageUtil.calculateDiskUsageForProject(p);
        }
        Assertions.assertEquals(sizeAll, project.getAction(ProjectDiskUsageAction.class).getAllDiskUsageWithoutBuilds(), "Calculation of job disk usage does not return right size of job and its sub-jobs without builds.");

    }

    @Test
    @LocalData
    void testCalculateDiskUsageWorkspaceForProject(JenkinsRule j) throws Exception {
        // turn off run listener
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Slave agent1 = DiskUsageTestUtil.createAgent("agent1", new File(j.jenkins.getRootDir(), "workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        Slave agent2 = DiskUsageTestUtil.createAgent("agent2", new File(j.jenkins.getRootDir(), "workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        FreeStyleProject project1 = j.createFreeStyleProject("project1");
        FreeStyleProject project2 = j.createFreeStyleProject("project2");
        project1.setAssignedNode(agent1);
        project2.setAssignedNode(agent1);
        j.buildAndAssertSuccess(project1);
        j.buildAndAssertSuccess(project2);
        project1.setAssignedNode(agent2);
        project2.setAssignedNode(agent2);
        j.buildAndAssertSuccess(project1);
        j.buildAndAssertSuccess(project2);
        File file = new File(agent1.getWorkspaceFor(project1).getRemote(), "fileList");
        File file2 = new File(agent2.getWorkspaceFor(project1).getRemote(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + agent1.getWorkspaceFor(project1).length();
        size += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file2)) + agent2.getWorkspaceFor(project1).length();
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        Assertions.assertEquals(size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of job workspace disk usage does not return right size.");
        file = new File(agent1.getWorkspaceFor(project2).getRemote(), "fileList");
        size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + agent1.getWorkspaceFor(project2).length() + agent2.getWorkspaceFor(project2).length();
        DiskUsageUtil.calculateWorkspaceDiskUsage(project2);
        Assertions.assertEquals(size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of job workspace disk usage does not return right size.");
    }

    @Test
    @LocalData
    void testCalculateDiskUsageWorkspaceForMatrixProjectWithConfigurationInSameDirectory(JenkinsRule j) throws Exception {
        // turn off run listener
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        j.jenkins.setNumExecutors(0);
        Slave agent1 = DiskUsageTestUtil.createAgent("agent1", new File(j.jenkins.getRootDir(), "workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        AxisList axes = new AxisList();
        TextAxis axis1 = new TextAxis("axis", "axis1 axis2 axis3");
        axes.add(axis1);
        MatrixProject project1 = j.jenkins.createProject(MatrixProject.class, "project1");
        project1.setAxes(axes);
        project1.setAssignedNode(agent1);
        j.buildAndAssertSuccess(project1);
        Slave agent2 = DiskUsageTestUtil.createAgent("agent2", new File(j.jenkins.getRootDir(), "workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        ArrayList<String> agents = new ArrayList<>();
        agents.add("agent2");
        LabelAxis axis2 = new LabelAxis("label", agents);
        axes.add(axis2);
        project1.setAxes(axes);
        File file = new File(agent1.getWorkspaceFor(project1).getRemote(), "fileList");
        File fileAxis1 = new File(agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis1", "fileList");
        File fileAxis2 = new File(agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis2", "fileList");
        File fileAxis3 = new File(agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis3", "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + agent1.getWorkspaceFor(project1).length();
        Long sizeAxis1 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis1)) + new File(
            agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis1").length();
        Long sizeAxis2 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis2)) + new File(
            agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis2").length();
        Long sizeAxis3 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis3)) + new File(
            agent1.getWorkspaceFor(project1).getRemote() + "/axis/axis3").length();
        for(MatrixConfiguration c: project1.getItems()) {
            DiskUsageUtil.calculateWorkspaceDiskUsage(c);
        }
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        Assertions.assertEquals(size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix job workspace disk usage does not return right size.");

        Assertions.assertEquals(sizeAxis1, project1.getItem("axis=axis1").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        Assertions.assertEquals(sizeAxis2, project1.getItem("axis=axis2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        Assertions.assertEquals(sizeAxis3, project1.getItem("axis=axis3").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");


        // next build - configuration are built on next agent
        // test if not active configuration are find and right counted
        // test if works with more complex configurations
        j.buildAndAssertSuccess(project1);
        for(MatrixConfiguration c: project1.getItems()) {
            DiskUsageUtil.calculateWorkspaceDiskUsage(c);
        }
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);

        Assertions.assertEquals(sizeAxis1, project1.getItem("axis=axis1").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        Assertions.assertEquals(sizeAxis2, project1.getItem("axis=axis2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        Assertions.assertEquals(sizeAxis3, project1.getItem("axis=axis3").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        fileAxis1 = new File(agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis1/label/agent2", "fileList");
        fileAxis2 = new File(agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis2/label/agent2", "fileList");
        fileAxis3 = new File(agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis3/label/agent2", "fileList");
        sizeAxis1 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis1)) + new File(
            agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis1/label/agent2").length();
        sizeAxis2 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis2)) + new File(
            agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis2/label/agent2").length();
        sizeAxis3 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis3)) + new File(
            agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis3/label/agent2").length();
        Assertions.assertEquals(sizeAxis1, project1.getItem("axis=axis1,label=agent2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        Assertions.assertEquals(sizeAxis2, project1.getItem("axis=axis2,label=agent2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");
        Assertions.assertEquals(sizeAxis3, project1.getItem("axis=axis3,label=agent2").getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix configuration workspace disk usage does not return right size.");


        // matrix project is built on the next agent
        // test if new folder on agent2 is counted too
        project1.setAssignedNode(agent2);
        j.buildAndAssertSuccess(project1);
        file = new File(agent2.getWorkspaceFor(project1).getRemote(), "fileList");
        size += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + agent2.getWorkspaceFor(project1).length();
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        Assertions.assertEquals(size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix job workspace disk usage does not return right size.");
    }

    @Test
    @LocalData
    void testCalculateDiskUsageWorkspaceWhenReferenceFromJobDoesNotExists(JenkinsRule j) throws Exception {
        // turn off run listener
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        DiskUsagePlugin plugin = j.jenkins.getPlugin(DiskUsagePlugin.class);
        plugin.getConfiguration().setCheckWorkspaceOnAgent(true);
        j.jenkins.setNumExecutors(0);
        Slave agent1 = DiskUsageTestUtil.createAgent("agent1", new File(j.jenkins.getRootDir(), "workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        AxisList axes = new AxisList();
        TextAxis axis1 = new TextAxis("axis", "axis1 axis2 axis3");
        axes.add(axis1);
        MatrixProject project1 = j.jenkins.createProject(MatrixProject.class, "project1");
        project1.setAxes(axes);
        project1.setAssignedNode(agent1);
        j.buildAndAssertSuccess(project1);
        Slave agent2 = DiskUsageTestUtil.createAgent("agent2", new File(j.jenkins.getRootDir(), "workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        File file = new File(agent1.getWorkspaceFor(project1).getRemote(), "fileList");
        Long size = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + agent1.getWorkspaceFor(project1).length();
        File fileAxis1 = new File(agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis1/label/agent2", "fileList");
        File fileAxis2 = new File(agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis2/label/agent2", "fileList");
        File fileAxis3 = new File(agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis3/label/agent2", "fileList");
        Long sizeAxis1 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis1)) + new File(
            agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis1/label/agent2").length();
        Long sizeAxis2 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis2)) + new File(
            agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis2/label/agent2").length();
        Long sizeAxis3 = DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(fileAxis3)) + new File(
            agent2.getWorkspaceFor(project1).getRemote() + "/axis/axis3/label/agent2").length();
        file = new File(agent2.getWorkspaceFor(project1).getRemote(), "fileList");
        size += DiskUsageTestUtil.getSize(DiskUsageTestUtil.readFileList(file)) + agent2.getWorkspaceFor(project1).length() + sizeAxis1 + sizeAxis2 + sizeAxis3;
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        Assertions.assertEquals(size, project1.getAction(ProjectDiskUsageAction.class).getDiskUsageWorkspace(), "Calculation of matrix job workspace disk usage does not return right size.");
        plugin.getConfiguration().setCheckWorkspaceOnAgent(false);
    }


    @Test
    void testCalculateDiskUsageWorkspaceUpdateIformationIfSavedWorkspaceDoesNotExists(JenkinsRule j) throws Exception {
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Slave agent1 = DiskUsageTestUtil.createAgent("agent1", new File(j.jenkins.getRootDir(), "workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        Slave agent2 = DiskUsageTestUtil.createAgent("agent2", new File(j.jenkins.getRootDir(), "workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        FreeStyleProject project1 = j.createFreeStyleProject("project1");
        project1.setAssignedNode(agent1);
        j.buildAndAssertSuccess(project1);

        DiskUsageProperty prop = project1.getProperty(DiskUsageProperty.class);
        if(prop == null) {
            prop = new DiskUsageProperty();
            project1.addProperty(prop);
        }
        prop.putAgentWorkspaceSize(agent2, agent2.getWorkspaceFor((TopLevelItem) project1).getRemote(), 54356l);
        DiskUsageUtil.calculateWorkspaceDiskUsage(project1);
        assertFalse(prop.getAgentWorkspaceUsage().containsKey(
            agent2.getNodeName()), "Agent agent2 should be removed from disk usage, because a workspace for project1 does not exist on this agent.");
        assertTrue(prop.getAgentWorkspaceUsage().containsKey(
            agent1.getNodeName()), "Disk usage should contains agent1, there is a workspace for project1.");
    }

    @Test
    void testParseExcludedJobsFromString(JenkinsRule j) throws Exception {
        FreeStyleProject projectWithSpace = j.createFreeStyleProject("Project with space");
        FreeStyleProject project = j.createFreeStyleProject("Project");
        FreeStyleProject project2 = j.createFreeStyleProject("Project2");
        FreeStyleProject projectWithSpace2 = j.createFreeStyleProject(" Project with space");
        String excluded = "Project with space,Project";
        List<String> excludedJobs = DiskUsageUtil.parseExcludedJobsFromString(excluded);
        assertTrue(excludedJobs.contains(project.getName()), "Excluded jobs should contains job without spaces in name");
        assertTrue(excludedJobs.contains(projectWithSpace.getName()), "Excluded jobs should contains job with spaces in name");
        excluded = "Project with space, Project";
        excludedJobs = DiskUsageUtil.parseExcludedJobsFromString(excluded);
        assertTrue(excludedJobs.contains(projectWithSpace.getName()), "Excluded jobs should parse jobs with spaces even if the space is used as separator.");
        assertFalse(excludedJobs.contains(projectWithSpace2.getName()), "Excluded jobs should parse jobs correctly even if the space is used as separator.");
        assertFalse(excludedJobs.contains(project2.getName()), "Excluded jobs should not contains jobs which is not occuren in excluded string.");
        excluded = "Project with space, Project5";
        excludedJobs = DiskUsageUtil.parseExcludedJobsFromString(excluded);
        assertFalse(excludedJobs.contains("Project5"), "Excluded jobs should not contains jobs which does not exists.");
        excluded = "Project with space, ";
        assertTrue(excludedJobs.contains(projectWithSpace.getName()) && excludedJobs.size() == 1, "Excluded jobs should be parsed correctly even if there additional separator");
    }

}
