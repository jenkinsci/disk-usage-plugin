package hudson.plugins.disk_usage.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hudson.plugins.disk_usage.*;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AperiodicWork;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
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
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author Lucie Votypkova
 */
public class JobDiskUsageCalculationThreadTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private void waitUntilThreadEnds(JobWithoutBuildsDiskUsageCalculation calculation) throws InterruptedException {
        while(calculation.isExecuting()) {
            Thread.sleep(100);
        }
    }

    private List<File> readFileList(File file) throws FileNotFoundException, IOException {
        List<File> files = new ArrayList<File>();
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
        Long length = 0l;
        for(File file: files) {
            length += file.length();
        }
        return length;
    }

    @Test
    @LocalData
    public void testExecute() throws IOException, InterruptedException {
        //turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        DiskUsageProjectActionFactory.DESCRIPTOR.enableJobsDiskUsageCalculation();
        FreeStyleProject project = (FreeStyleProject) j.jenkins.getItem("project1");
        FreeStyleProject project2 = (FreeStyleProject) j.jenkins.getItem("project2");
        //we need all build information are loaded before counting
        project.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        project2.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        File file = new File(project.getRootDir(), "fileList");
        Long projectSize = getSize(readFileList(file)) + project.getRootDir().length();
        file = new File(project2.getRootDir(), "fileList");
        Long project2Size = getSize(readFileList(file)) + project2.getRootDir().length();
        projectSize += project.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        project2Size += project2.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        JobWithoutBuildsDiskUsageCalculation calculation = new JobWithoutBuildsDiskUsageCalculation();
        if(calculation.isExecuting()) {
            DiskUsageTestUtil.cancelCalculation(calculation);
        }
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        assertEquals("Project project has wrong job size.", projectSize, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
        assertEquals("Project project2 has wrong job size.", project2Size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
    }

    @Test
    @LocalData
    public void testMatrixProject() throws IOException, InterruptedException {
        //turn off run listener
        DiskUsageProjectActionFactory.DESCRIPTOR.enableJobsDiskUsageCalculation();
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Map<String, Long> matrixConfigurationsSize = new TreeMap<String, Long>();
        MatrixProject project = (MatrixProject) j.jenkins.getItem("project1");
        FreeStyleProject project2 = (FreeStyleProject) j.jenkins.getItem("project2");
        //we need all build information are loaded before counting
        project.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        project2.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
        File file = new File(project.getRootDir(), "fileList");
        Long projectSize = getSize(readFileList(file)) + project.getRootDir().length();
        file = new File(project2.getRootDir(), "fileList");
        Long project2Size = getSize(readFileList(file)) + project2.getRootDir().length();
        projectSize += project.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        project2Size += project2.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
        for(MatrixConfiguration config: project.getItems()) {
            config.getProperty(DiskUsageProperty.class).getDiskUsage().loadAllBuilds();
            File f = new File(config.getRootDir(), "fileList");
            Long size = getSize(readFileList(f)) + config.getRootDir().length();
            long diskUsageXML = config.getProperty(DiskUsageProperty.class).getProjectDiskUsage().getConfigFile().getFile().length();
            matrixConfigurationsSize.put(config.getDisplayName(), size + diskUsageXML);
        }
        JobWithoutBuildsDiskUsageCalculation calculation = new JobWithoutBuildsDiskUsageCalculation();
        if(calculation.isExecuting()) {
            DiskUsageTestUtil.cancelCalculation(calculation);
        }
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        assertEquals("Project project has wrong job size.", projectSize, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
        assertEquals("Project project2 has wrong job size.", project2Size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
        for(MatrixConfiguration config: project.getItems()) {
            assertEquals("Configuration " + config.getDisplayName() + " has wrong job size.", matrixConfigurationsSize.get(config.getDisplayName()), config.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
        }
    }

    public void testDoNotExecuteDiskUsageWhenPreviousCalculationIsInProgress() throws Exception {
        JobWithoutBuildsDiskUsageCalculation calculation = new JobWithoutBuildsDiskUsageCalculation();
        DiskUsageTestUtil.cancelCalculation(calculation);
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, j.contextPath);
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
        assertEquals("Disk usage should not start calculation if preview calculation is in progress.", 0l, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
        t.interrupt();
    }

    public void testDoNotCalculateUnenabledDiskUsage() throws Exception {
        FreeStyleProject projectWithoutDiskUsage = j.jenkins.createProject(FreeStyleProject.class, "projectWithoutDiskUsage");
        DiskUsageProjectActionFactory.DESCRIPTOR.disableJobsDiskUsageCalculation();
        JobWithoutBuildsDiskUsageCalculation calculation = AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class);
        calculation.execute(TaskListener.NULL);
        assertEquals("Disk usage for build should not be counted.", 0, projectWithoutDiskUsage.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds(), 0);
        DiskUsageProjectActionFactory.DESCRIPTOR.enableJobsDiskUsageCalculation();
    }

    @Test
    public void testDoNotCalculateExcludedJobs() throws Exception {
        JobWithoutBuildsDiskUsageCalculation calculation = AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class);
        if(calculation.isExecuting()) {
            DiskUsageTestUtil.cancelCalculation(calculation);
        }
        FreeStyleProject exludedJob = j.jenkins.createProject(FreeStyleProject.class, "excludedJob");
        FreeStyleProject includedJob = j.jenkins.createProject(FreeStyleProject.class, "incudedJob");
        List<String> excludes = new ArrayList<String>();
        excludes.add(exludedJob.getName());
        DiskUsageProjectActionFactory.DESCRIPTOR.setExcludedJobs(excludes);
        calculation.execute(TaskListener.NULL);
        assertEquals("Disk usage for excluded project should not be counted.", 0, exludedJob.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds(), 0);
        assertTrue("Disk usage for included project should be not be counted.", includedJob.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds() > 0);
        excludes.clear();
    }

}
