package hudson.plugins.disk_usage.integration;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import hudson.FilePath;
import hudson.Functions;
import hudson.XmlFile;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.model.TopLevelItem;
import hudson.model.listeners.RunListener;
import hudson.plugins.disk_usage.DiskUsageBuildInformation;
import hudson.plugins.disk_usage.DiskUsageBuildListener;
import hudson.plugins.disk_usage.DiskUsagePlugin;
import hudson.plugins.disk_usage.DiskUsageProperty;
import hudson.plugins.disk_usage.ProjectDiskUsage;
import hudson.plugins.promoted_builds.JobPropertyImpl;
import hudson.plugins.promoted_builds.PromotionProcess;
import hudson.plugins.promoted_builds.conditions.SelfPromotionCondition;
import hudson.slaves.OfflineCause;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ConcurrentModificationException;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
public class DiskUsagePropertyTest {

    @Issue("JENKINS-40728")
    @Test
    void testCalculationWorkspaceForItemInNonTopLeverGroupItem(JenkinsRule j) throws Exception {
        final var project = j.createFreeStyleProject("some-project");
        JobPropertyImpl property = new JobPropertyImpl(project);
        project.addProperty(property);
        PromotionProcess process = property.addProcess("Simple-process");
        process.conditions.add(new SelfPromotionCondition(true));
        process.getBuildSteps().add(new Shell("echo hello > log.log"));
        j.buildAndAssertSuccess(project);
        DiskUsageProperty p = process.getProperty(DiskUsageProperty.class);
        Thread.sleep(1000);
        p.getAllNonAgentOrCustomWorkspaceSize();
    }

    @Test
    void testGetAllDiskUsageWithoutBuilds(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        MatrixProject matrixProject = j.jenkins.createProject(MatrixProject.class, "project2");
        TextAxis axis1 = new TextAxis("axis", "axisA", "axisB", "axisC");
        TextAxis axis2 = new TextAxis("axis2", "Aaxis", "Baxis", "Caxis");
        AxisList list = new AxisList();
        list.add(axis1);
        list.add(axis2);
        matrixProject.setAxes(list);
        Long sizeOfProject = 7546L;
        Long sizeOfMatrixProject = 6800L;
        DiskUsageProperty projectProperty = project.getProperty(DiskUsageProperty.class);
        projectProperty.setDiskUsageWithoutBuilds(sizeOfProject);
        DiskUsageProperty matrixProjectProperty = matrixProject.getProperty(DiskUsageProperty.class);
        matrixProjectProperty.setDiskUsageWithoutBuilds(sizeOfMatrixProject);
        long size1 = 5390;
        int count = 1;
        Long matrixProjectTotalSize = sizeOfMatrixProject;
        for(MatrixConfiguration c: matrixProject.getItems()) {
            DiskUsageProperty configurationProperty = new DiskUsageProperty();
            c.addProperty(configurationProperty);
            configurationProperty.setDiskUsageWithoutBuilds(count * size1);
            matrixProjectTotalSize += count * size1;
            count++;
        }
        assertEquals(sizeOfProject, project.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds(), "DiskUsageProperty for FreeStyleProject " + project.getDisplayName() + " returns wrong value its size without builds and including sub-projects.");
        assertEquals(matrixProjectTotalSize, matrixProject.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds(), "DiskUsageProperty for MatrixProject " + project.getDisplayName() + " returns wrong value for its size without builds and including sub-projects.");
    }

    @Test
    void testCheckWorkspaces(JenkinsRule j) throws Exception {
        // turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Slave agent1 = j.createOnlineSlave();
        Slave agent2 = j.createOnlineSlave();
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        project.setAssignedLabel(agent1.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setAssignedLabel(agent2.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.getBuildByNumber(1).delete();
        DiskUsageProperty prop = project.getProperty(DiskUsageProperty.class);
        if(prop == null) {
            prop = new DiskUsageProperty();
            project.addProperty(prop);
        }
        prop.checkWorkspaces();
        Set<String> nodes = prop.getAgentWorkspaceUsage().keySet();
        assertTrue(nodes.contains(
            agent2.getNodeName()), "DiskUsage property should contains agent " + agent2.getDisplayName() + " in agentWorkspaceUsage.");
        assertFalse(nodes.contains(
            agent1.getNodeName()), "DiskUsage property should not contains agent " + agent1.getDisplayName() + " in agentWorkspaceUsage when detection of user workspace without reference from project is not set.");
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().setCheckWorkspaceOnAgent(true);
        prop.checkWorkspaces();
        assertTrue(nodes.contains(
            agent2.getNodeName()), "DiskUsage property should contains agent " + agent2.getDisplayName() + " in agentWorkspaceUsage.");
        assertTrue(nodes.contains(
            agent1.getNodeName()), "DiskUsage property should contains agent " + agent1.getDisplayName() + " in agentWorkspaceUsage when detection of user workspace without reference from project is set.");
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().setCheckWorkspaceOnAgent(false);
    }

    @Test
    void getWorkspaceSizeTest(JenkinsRule j) throws Exception {
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Slave agent1 = DiskUsageTestUtil.createAgent("agent1", new File(j.jenkins.getRootDir(), "workspace1").getPath(), j.jenkins, j.createComputerLauncher(null));
        Slave agent2 = DiskUsageTestUtil.createAgent("agent2", new File(j.jenkins.getRootDir(), "workspace2").getPath(), j.jenkins, j.createComputerLauncher(null));
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        project.setAssignedLabel(agent1.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setAssignedLabel(agent2.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(j.jenkins.getRootDir().getAbsolutePath() + "/project-custom-workspace");
        j.buildAndAssertSuccess(project);
        DiskUsageProperty prop = project.getProperty(DiskUsageProperty.class);
        if(prop == null) {
            prop = new DiskUsageProperty();
            project.addProperty(prop);
        }
        prop.checkWorkspaces();
        Long workspaceSize = 7509L;
        Map<String, Map<String, Long>> diskUsage = prop.getAgentWorkspaceUsage();
        for(String name: diskUsage.keySet()) {
            Map<String, Long> agentInfo = diskUsage.get(name);
            for(String path: agentInfo.keySet()) {
                agentInfo.put(path, workspaceSize);
            }
        }
        assertEquals(workspaceSize * 2, prop.getWorkspaceSize(true), 0, "DiskUsage workspaces which is configured as agent workspace is wrong.");
        assertEquals(workspaceSize, prop.getWorkspaceSize(false), 0, "DiskUsage workspaces which is not configured as agent workspace is wrong.");
    }

    @Test
    void testcheckWorkspacesIfAgentIsDeleted(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        DiskUsageProperty property = new DiskUsageProperty();
        project.addProperty(property);
        Slave agent1 = j.createOnlineSlave();
        Slave agent2 = j.createOnlineSlave();
        agent1.getWorkspaceRoot().mkdirs();
        agent2.getWorkspaceRoot().mkdirs();
        FilePath path = j.jenkins.getWorkspaceFor(project);
        path.mkdirs();
        property.putAgentWorkspaceSize(j.jenkins, path.getRemote(), 10495l);
        property.putAgentWorkspaceSize(agent1, agent1.getRemoteFS(), 5670l);
        property.putAgentWorkspaceSize(agent2, agent2.getRemoteFS(), 7987l);
        j.jenkins.removeNode(agent2);
        property.checkWorkspaces();
        assertFalse(property.getAgentWorkspaceUsage().containsKey(
            agent2.getNodeName()), "Disk usage property should not contains agent which does not exist.");
        assertTrue(property.getAgentWorkspaceUsage().containsKey(agent1.getNodeName()), "Disk usage property should contains agent1.");
        assertTrue(property.getAgentWorkspaceUsage().containsKey(j.jenkins.getNodeName()), "Disk usage property should contains jenkins master.");
    }

    @Test
    void testCheckWorkspacesIfDoesNotExistsIsDeleted(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        DiskUsageProperty property = new DiskUsageProperty();
        project.addProperty(property);
        Slave agent1 = j.createOnlineSlave();
        Slave agent2 = j.createOnlineSlave();
        agent1.getWorkspaceRoot().mkdirs();
        agent2.getWorkspaceRoot().mkdirs();
        FilePath path = j.jenkins.getWorkspaceFor(project);
        path.mkdirs();
        property.putAgentWorkspaceSize(j.jenkins, path.getRemote(), 10495l);
        property.putAgentWorkspaceSize(agent1, agent1.getRemoteFS() + "/project", 5670l);
        property.putAgentWorkspaceSize(agent2, agent2.getRemoteFS(), 7987l);
        property.checkWorkspaces();
        assertFalse(property.getAgentWorkspaceUsage().containsKey(
            agent1.getNodeName()), "Disk usage property should not contains agent which does not have any workspace for its project.");
        assertTrue(property.getAgentWorkspaceUsage().containsKey(agent2.getNodeName()), "Disk usage property should contains agent2.");
        assertTrue(property.getAgentWorkspaceUsage().containsKey(j.jenkins.getNodeName()), "Disk usage property should contains jenkins master.");
        path.delete();
        property.checkWorkspaces();
        assertFalse(property.getAgentWorkspaceUsage().containsKey(j.jenkins.getNodeName()), "Disk usage property should contains jenkins master, because workspace for its project was deleted.");

    }

    @Test
    void testGetAllNonAgentOrCustomWorkspaceSizeWithOnlyAgents(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        if(Functions.isWindows()) {
            project.getBuildersList().add(new BatchFile("echo hello > log"));
        } else {
            project.getBuildersList().add(new Shell("echo hello > log"));
        }
        Slave agent3 = DiskUsageTestUtil.createAgent("agent3", new File(j.jenkins.getRootDir(), "AgentWorkspace").getPath(), j.jenkins, j.createComputerLauncher(null));
        Slave agent1 = j.createOnlineSlave();
        Slave agent2 = j.createOnlineSlave();

        File workspaceAgent1 = new File(agent3.getRemoteFS(), project.getName() + "/log");
        File workspaceAgent2 = new File(agent1.getRemoteFS(), project.getName() + "/log");
        File customWorkspaceAgent1 = new File(j.jenkins.getRootDir(), "custom2/log");
        File customWorkspaceAgent2 = new File(j.jenkins.getRootDir(), "custom1/log");

        project.setAssignedLabel(agent3.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(customWorkspaceAgent1.getParentFile().getAbsolutePath());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(null);
        project.setAssignedLabel(agent2.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(customWorkspaceAgent2.getParentFile().getAbsolutePath());
        j.buildAndAssertSuccess(project);
        Long customWorkspaceAgentSize = customWorkspaceAgent1.length() + customWorkspaceAgent2.length() + customWorkspaceAgent1.getParentFile().length() + customWorkspaceAgent2.getParentFile().length();
        assertEquals(customWorkspaceAgentSize, project.getProperty(DiskUsageProperty.class).getAllNonAgentOrCustomWorkspaceSize(), 0, "");
        // take one agent offline
        agent1.toComputer().disconnect(new OfflineCause.ByCLI("test disconnection"));
        assertEquals(customWorkspaceAgentSize, project.getProperty(DiskUsageProperty.class).getAllNonAgentOrCustomWorkspaceSize(), 0, "");
    }

    @Test
    void testGetAllNonAgentOrCustomWorkspaceSizeWithMaster(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project");
        if(Functions.isWindows()) {
            project.getBuildersList().add(new BatchFile("echo hello > log"));
        } else {
            project.getBuildersList().add(new Shell("echo hello > log"));
        }
        Slave agent1 = j.createOnlineSlave();
        File workspaceAgent2 = new File(agent1.getRemoteFS(), project.getName() + "/log");
        File customWorkspaceAgent1 = new File(j.jenkins.getRootDir(), "custom2/log");
        File customWorkspaceAgent2 = new File(j.jenkins.getRootDir(), "custom1/log");
        j.jenkins.setNumExecutors(1);
        project.setAssignedLabel(j.jenkins.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(customWorkspaceAgent1.getParentFile().getAbsolutePath());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(null);
        project.setAssignedLabel(agent1.getSelfLabel());
        j.buildAndAssertSuccess(project);
        project.setCustomWorkspace(customWorkspaceAgent2.getParentFile().getAbsolutePath());
        j.buildAndAssertSuccess(project);
        Long customWorkspaceAgentSize = customWorkspaceAgent1.length() + customWorkspaceAgent2.length() + customWorkspaceAgent1.getParentFile().length() + customWorkspaceAgent2.getParentFile().length();
        assertEquals(customWorkspaceAgentSize, project.getProperty(DiskUsageProperty.class).getAllNonAgentOrCustomWorkspaceSize(), 0, "");
        // take one agent offline
        j.jenkins.setNumExecutors(0);
        assertEquals(customWorkspaceAgentSize, project.getProperty(DiskUsageProperty.class).getAllNonAgentOrCustomWorkspaceSize(), 0, "");
    }

    @Test
    @ReplaceHudsonHomeWithCurrentPath("jobs/project1/config.xml")
    @LocalData
    void testBackwadrCompatibility2(JenkinsRule j) throws IOException {
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().disableBuildsDiskUsageCalculation();
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().disableJobsDiskUsageCalculation();
        j.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration().disableWorkspacesDiskUsageCalculation();
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        property.getDiskUsage().loadAllBuilds();
        assertEquals(188357L, property.getAllDiskUsageWithoutBuilds(), 0, "Size of project1 should be loaded from previous configuration.");
        assertEquals(4096L, property.getAllWorkspaceSize(), 0, "Size of workspaces should be loaded from previous configuration.");
        assertTrue(property.getAgentWorkspaceUsage().get("").containsKey(j.jenkins.getRootDir().getAbsolutePath() + "/workspace"), "Path of workspace should be loaded form previous configuration.");
    }

    @Test
    @LocalData
    void testGetDiskUsageOfBuilds(JenkinsRule j) {
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        for(DiskUsageBuildInformation information: property.getDiskUsageOfBuilds()) {
            assertEquals(information.getNumber() * 1000, information.getSize(), 0, "Disk usage of build has loaded wrong size.");
        }
        assertEquals(loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0, "No build should be loaded.");
    }


    @Test
    @LocalData
    void testGetDiskUsageOfBuild(JenkinsRule j) {
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertEquals(3000, property.getDiskUsageOfBuild("1"), 0, "Build with id 1 should have size 3000");
        assertEquals(10000, property.getDiskUsageOfBuild("7"), 0, "Build with id 7 should have size 10000");
        assertEquals(loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0, "No build should be loaded.");
    }

    @Test
    @LocalData
    void testGetDiskUsageBuildInformation(JenkinsRule j) {
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertEquals(3000, property.getDiskUsageBuildInformation("1").getSize(), 0, "Build with id 1 should have size 3000");
        assertEquals(10000, property.getDiskUsageBuildInformation("7").getSize(), 0, "Build with id 7 should have size 10000");
        assertEquals(loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0, "No build should be loaded.");
    }

    @Test
    @LocalData
    void testGetDiskUsageOfBuildByNumber(JenkinsRule j) {
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertEquals(3000, property.getDiskUsageOfBuild(1), 0, "Build with id 1 should have size 3000");
        assertEquals(10000, property.getDiskUsageOfBuild(7), 0, "Build with id 7 should have size 10000");
        assertEquals(loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0, "No build should be loaded.");

    }

    @Test
    @ReplaceHudsonHomeWithCurrentPath("jobs/project1/disk-usage.xml")
    @LocalData
    void testCheckWorkspacesBuildsWithoutLoadingBuilds(JenkinsRule j) throws IOException, InterruptedException {
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuildsSize = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        FilePath f = j.jenkins.getWorkspaceFor((TopLevelItem) project);
        property.checkWorkspaces();
        assertEquals(4096, property.getAllWorkspaceSize(), 0, "Workspace should have size 4096");
        assertEquals(loadedBuildsSize, project._getRuns().getLoadedBuilds().size(), 0, "No build should be loaded.");
    }

    @Test
    @ReplaceHudsonHomeWithCurrentPath("jobs/project1/config.xml, jobs/project1/builds/1/build.xml, jobs/project1/builds/3/build.xml")
    @LocalData
    void testCheckWorkspacesWithLoadingBuilds(JenkinsRule j) throws IOException {
        File file = new File(j.jenkins.getRootDir(), "jobs/project2/builds/1/build.xml");
        XmlFile f = new XmlFile(new XStream2(), file);
        String newBuildXml = f.asString().replace("${JENKINS_HOME}", j.jenkins.getRootDir().getAbsolutePath());
        PrintStream st = new PrintStream(file);
        st.print(newBuildXml);
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        AbstractProject project2 = (AbstractProject) j.jenkins.getItem("project2");
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        DiskUsageProperty property2 = (DiskUsageProperty) project2.getProperty(DiskUsageProperty.class);
        property2.getDiskUsage().loadAllBuilds();
        assertTrue(property.getAgentWorkspaceUsage().get("").containsKey("${JENKINS_HOME}/jobs/project1/workspace"), "Project should contains workspace with path {JENKINS_HOME}/jobs/project1/workspace");
        assertTrue(property2.getAgentWorkspaceUsage().get("").containsKey(j.jenkins.getRootDir().getAbsolutePath() + "/workspace"), "Project should contains workspace with path {JENKINS_HOME}/workspace");

        assertEquals(2, project2._getRuns().getLoadedBuilds().size(), 0, "Builds should be loaded.");
    }

    @Test
    void testGetAllDiskUsageOfBuild(JenkinsRule j) throws IOException, Exception {
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
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
        Long sizeofBuild = 7546L;
        Long sizeOfMatrixBuild1 = 6800L;
        Long sizeOfMatrixBuild2 = 14032L;
        DiskUsageTestUtil.getBuildDiskUsageAction(build).setDiskUsage(sizeofBuild);
        DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild1).setDiskUsage(sizeOfMatrixBuild1);
        DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild2).setDiskUsage(sizeOfMatrixBuild2);
        long size1 = 5390;
        long size2 = 2390;
        int count = 1;
        Long matrixBuild1TotalSize = sizeOfMatrixBuild1;
        Long matrixBuild2TotalSize = sizeOfMatrixBuild2;
        for(MatrixConfiguration c: matrixProject.getItems()) {
            AbstractBuild configurationBuild = c.getBuildByNumber(1);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild).setDiskUsage(count * size1);
            matrixBuild1TotalSize += count * size1;
            AbstractBuild configurationBuild2 = c.getBuildByNumber(2);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild2).setDiskUsage(count * size2);
            matrixBuild2TotalSize += count * size2;
            count++;
        }
        hudson.plugins.disk_usage.DiskUsageProperty freeStyleProjectProperty = project.getProperty(DiskUsageProperty.class);
        DiskUsageProperty matrixProjectProperty = matrixProject.getProperty(DiskUsageProperty.class);
        assertEquals(sizeofBuild, freeStyleProjectProperty.getAllDiskUsageOfBuild(1), "BuildDiskUsageAction for build 1 of FreeStyleProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.");
        assertEquals(matrixBuild1TotalSize, matrixProjectProperty.getAllDiskUsageOfBuild(1), "BuildDiskUsageAction for build 1 of MatrixProject " + matrixProject.getDisplayName() + " returns wrong value for its size including sub-builds.");
        assertEquals(matrixBuild2TotalSize, matrixProjectProperty.getAllDiskUsageOfBuild(2), "BuildDiskUsageAction for build 2 of MatrixProject " + matrixProject.getDisplayName() + " returns wrong value for its size including sub-builds.");

    }

    @Test
    @LocalData
    void testDoNotBreakLazyLoading(JenkinsRule j) {
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        assertTrue(8 > loadedBuilds, "This tests does not sense if there are loaded all builds.");
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertEquals(1000, property.getAllDiskUsageOfBuild(8), 0, "Size of builds should be loaded.");
        assertEquals(7000, property.getAllDiskUsageOfBuild(4), 0, "Size of builds should be loaded.");
        assertTrue(loadedBuilds <= project._getRuns().getLoadedBuilds().size(), "No new build should be loaded.");
    }

    @Test
    void testRemoveBuild(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        assertEquals(2, property.getDiskUsage().getBuildDiskUsage(false).size(), "Disk usage should have information about two builds.");
        AbstractBuild build = project.getLastBuild();
        build.delete();
        assertEquals(1, property.getDiskUsage().getBuildDiskUsage(false).size(), "Deleted build should be removed from disk-usage informations too.");
    }

    @Test
    void testRemoveDeletedBuildNotLoadedByJenkins(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        AbstractBuild build = project.getLastBuild();
        File file = build.getRootDir();
        FilePath path = new FilePath(file);
        path.deleteRecursive();
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        assertFalse(file.exists(), "It is not possible to delete build.");
        assertEquals(2, property.getDiskUsage().getBuildDiskUsage(false).size(), "Disk usage should have information about 2 builds.");
        j.jenkins.reload();
        project = (FreeStyleProject) j.jenkins.getItem(project.getDisplayName());
        property = project.getProperty(DiskUsageProperty.class);
        assertEquals(1, property.getDiskUsage().getBuildDiskUsage(false).size(), "Deleted build without Jenkins should not be loaded.");

    }

    private TestThread runRemoveThread(ProjectDiskUsage usage) {
        final ProjectDiskUsage diskUsage = usage;
        TestThread removeThread = new TestThread("removeFromSet"){
            public void run() {
                try {
                    int count = 0;
                    while(count < 1000) {
                        count++;
                        diskUsage.removeBuild(diskUsage.getDiskUsageBuildInformation(count));
                    }
                } catch (ConcurrentModificationException ex) {
                    exception = ex;
                } catch (Exception ex) {
                    exception = ex;
                }
            }
        };
        removeThread.start();
        return removeThread;
    }

    private TestThread runPutThread(ProjectDiskUsage usage) {
        final ProjectDiskUsage diskUsage = usage;
        TestThread putThread = new TestThread("putIntoSet"){
            public void run() {
                try {
                    int count = 0;
                    while(count < 1000) {
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
                } catch (Exception ex) {
                    exception = ex;
                }
            }
        };
        putThread.start();
        return putThread;
    }

    private TestThread runSaveThread(ProjectDiskUsage usage) {
        final ProjectDiskUsage diskUsage = usage;
        TestThread saveThread = new TestThread("saveSet"){
            public void run() {
                try {
                    int count = 0;
                    while(count < 100) {
                        count++;
                        diskUsage.save();
                    }
                } catch (ConcurrentModificationException ex) {
                    exception = ex;
                } catch (Exception ex) {
                    exception = ex;
                }
            }
        };
        saveThread.start();
        return saveThread;
    }

    private void checkForConcurrencyException(Exception exception) {
        exception.printStackTrace();
        if(exception instanceof ConcurrentModificationException) {
            fail("DiskUsageProperty is not thread save. Attribute #diskUsageProperty caused ConcurrentModitifiactionException");
            return;
        }
        fail("Checking of thread safety caused Exception which is not connected with thread safety problem.");
    }

    @Issue("JENKINS-29143")
    @Test
    void testThreadSaveOperationUnderSetOfDiskUsageBuildInformation(JenkinsRule j) throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        final ProjectDiskUsage diskUsage = new ProjectDiskUsage();
        diskUsage.setProject(project);
        TestThread putThread = runPutThread(diskUsage);
        TestThread removeThread = runRemoveThread(diskUsage);
        TestThread saveThread = runSaveThread(diskUsage);
        while(putThread.isAlive() || removeThread.isAlive() || saveThread.isAlive()) {
            Thread.currentThread().sleep(1000);
        }

        Exception ex = putThread.getException();
        if(putThread.getException() != null) {
            checkForConcurrencyException(ex);
        }
        ex = removeThread.getException();
        if(removeThread.getException() != null) {
            checkForConcurrencyException(ex);
        }
        ex = saveThread.getException();
        if(saveThread.getException() != null) {
            checkForConcurrencyException(ex);
        }
    }

    public class TestThread extends Thread {

        TestThread(String name) {
            super(name);
        }

        public Exception exception;

        public Exception getException() {
            return exception;
        }

    }


    @Target(METHOD)
    @Retention(RUNTIME)
    @JenkinsRecipe(ReplaceHudsonHomeWithCurrentPath.CurrentWorkspacePath.class)
    public @interface ReplaceHudsonHomeWithCurrentPath {

        String value() default "";

        class CurrentWorkspacePath extends JenkinsRecipe.Runner<ReplaceHudsonHomeWithCurrentPath> {
            private String paths;

            public void decorateHome(JenkinsRule rule, File home) {
                if(paths.isEmpty()) {
                    return;
                }
                for(String path: paths.split(",")) {
                    path = path.trim();
                    try {
                        File file = new File(home, path);
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

    class WorkspacePathAnnotation implements Annotation {

        public Class<? extends Annotation> annotationType() {
            return WorkspacePathAnnotation.class;
        }

    }


}
