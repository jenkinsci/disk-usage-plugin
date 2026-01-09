package hudson.plugins.disk_usage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AperiodicWork;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.disk_usage.DiskUsageBuildListener;
import hudson.plugins.disk_usage.DiskUsageProjectActionFactory;
import hudson.plugins.disk_usage.DiskUsageProperty;
import hudson.plugins.disk_usage.JobWithoutBuildsDiskUsageCalculation;
import hudson.plugins.disk_usage.ProjectDiskUsageAction;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
public class JobDiskUsageCalculationThreadTest {

    private void waitUntilThreadEnds(JobWithoutBuildsDiskUsageCalculation calculation) throws InterruptedException {
        while(calculation.isExecuting()) {
            Thread.sleep(100);
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

    @Test
    @LocalData
    void testExecute(JenkinsRule j) throws IOException, InterruptedException {
        // turn off run listener
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        DiskUsageProjectActionFactory.DESCRIPTOR.enableJobsDiskUsageCalculation();
        FreeStyleProject project = (FreeStyleProject) j.jenkins.getItem("project1");
        FreeStyleProject project2 = (FreeStyleProject) j.jenkins.getItem("project2");
        // we need all build information are loaded before counting
        project.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        project2.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        File file = new File(project.getRootDir(), "fileList");
        long projectSize = getSize(readFileList(file)) + project.getRootDir().length();
        file = new File(project2.getRootDir(), "fileList");
        long project2Size = getSize(readFileList(file)) + project2.getRootDir().length();
        projectSize += project.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        project2Size += project2.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        JobWithoutBuildsDiskUsageCalculation calculation = new JobWithoutBuildsDiskUsageCalculation();
        if(calculation.isExecuting()) {
            DiskUsageTestUtil.cancelCalculation(calculation);
        }
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        assertEquals(projectSize, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0, "Project project has wrong job size.");
        assertEquals(project2Size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0, "Project project2 has wrong job size.");
    }

    @Test
    @LocalData
    void testMatrixProject(JenkinsRule j) throws IOException, InterruptedException {
        // turn off run listener
        DiskUsageProjectActionFactory.DESCRIPTOR.enableJobsDiskUsageCalculation();
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Map<String, Long> matrixConfigurationsSize = new TreeMap<>();
        MatrixProject project = (MatrixProject) j.jenkins.getItem("project1");
        FreeStyleProject project2 = (FreeStyleProject) j.jenkins.getItem("project2");
        // we need all build information are loaded before counting
        project.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        project2.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        File file = new File(project.getRootDir(), "fileList");
        long projectSize = getSize(readFileList(file)) + project.getRootDir().length();
        file = new File(project2.getRootDir(), "fileList");
        long project2Size = getSize(readFileList(file)) + project2.getRootDir().length();
        projectSize += project.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        project2Size += project2.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        for(MatrixConfiguration config: project.getItems()) {
            config.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
            File f = new File(config.getRootDir(), "fileList");
            long size = getSize(readFileList(f)) + config.getRootDir().length();
            long diskUsageXML = config.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
            matrixConfigurationsSize.put(config.getDisplayName(), size + diskUsageXML);
        }
        JobWithoutBuildsDiskUsageCalculation calculation = new JobWithoutBuildsDiskUsageCalculation();
        if(calculation.isExecuting()) {
            DiskUsageTestUtil.cancelCalculation(calculation);
        }
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        assertEquals(projectSize, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0, "Project project has wrong job size.");
        assertEquals(project2Size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0, "Project project2 has wrong job size.");
        for(MatrixConfiguration config: project.getItems()) {
            assertEquals(matrixConfigurationsSize.get(config.getDisplayName()), config.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0, "Configuration " + config.getDisplayName() + " has wrong job size.");
        }
    }

    @Test
    void testDoNotExecuteDiskUsageWhenPreviousCalculationIsInProgress(JenkinsRule j) throws Exception {
        JobWithoutBuildsDiskUsageCalculation calculation = new JobWithoutBuildsDiskUsageCalculation();
        DiskUsageTestUtil.cancelCalculation(calculation);
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "freestyle1");
        final JobWithoutBuildsDiskUsageCalculation testCalculation = new JobWithoutBuildsDiskUsageCalculation();
        Thread t = new Thread(testCalculation.getThreadName()){
            @Override
            public void run() {
                try {
                    Thread.sleep(10000);
                } catch (Exception ex) {
                    Logger.getLogger(JobDiskUsageCalculationThreadTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        t.start();
        Thread.sleep(1000);
        testCalculation.doRun();
        assertEquals(0l, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0, "Disk usage should not start calculation if preview calculation is in progress.");
        t.interrupt();
    }

    @Test
    void testDoNotCalculateUnenabledDiskUsage(JenkinsRule j) throws Exception {
        FreeStyleProject projectWithoutDiskUsage = j.jenkins.createProject(FreeStyleProject.class, "projectWithoutDiskUsage");
        DiskUsageProjectActionFactory.DESCRIPTOR.disableJobsDiskUsageCalculation();
        JobWithoutBuildsDiskUsageCalculation calculation = AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class);
        calculation.execute(TaskListener.NULL);
        assertEquals(0, projectWithoutDiskUsage.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds(), 0, "Disk usage for build should not be counted.");
        DiskUsageProjectActionFactory.DESCRIPTOR.enableJobsDiskUsageCalculation();
    }

    @Test
    void testDoNotCalculateExcludedJobs(JenkinsRule j) throws Exception {
        JobWithoutBuildsDiskUsageCalculation calculation = AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class);
        if(calculation.isExecuting()) {
            DiskUsageTestUtil.cancelCalculation(calculation);
        }
        FreeStyleProject excludedJob = j.jenkins.createProject(FreeStyleProject.class, "excludedJob");
        FreeStyleProject includedJob = j.jenkins.createProject(FreeStyleProject.class, "includedJob");
        List<String> excludes = new ArrayList<>();
        excludes.add(excludedJob.getName());
        DiskUsageProjectActionFactory.DESCRIPTOR.setExcludedJobs(excludes);
        calculation.execute(TaskListener.NULL);
        assertEquals(0, excludedJob.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds(), 0, "Disk usage for excluded project should not be counted.");
        assertTrue(includedJob.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds() > 0, "Disk usage for included project should be not be counted.");
        excludes.clear();
    }

}
