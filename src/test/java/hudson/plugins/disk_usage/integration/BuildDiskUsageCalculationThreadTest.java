package hudson.plugins.disk_usage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.disk_usage.BuildDiskUsageCalculationThread;
import hudson.plugins.disk_usage.DiskUsageBuildListener;
import hudson.plugins.disk_usage.DiskUsageProjectActionFactory;
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
public class BuildDiskUsageCalculationThreadTest {

    private void waitUntilThreadEnds(BuildDiskUsageCalculationThread calculation) throws InterruptedException {
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

    @Test
    @LocalData
    void testExecute(JenkinsRule j) throws IOException, InterruptedException {
        // turn off run listener
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Map<AbstractBuild<?,?>, Long> buildSizesProject1 = new TreeMap<>();
        Map<AbstractBuild<?,?>, Long> buildSizesProject2 = new TreeMap<>();
        FreeStyleProject project = (FreeStyleProject) j.jenkins.getItem("project1");
        FreeStyleProject project2 = (FreeStyleProject) j.jenkins.getItem("project2");
        for(AbstractBuild<?,?> build: project.getBuilds()) {
            File file = new File(build.getRootDir(), "fileList");
            buildSizesProject1.put(build, getSize(readFileList(file)) + build.getRootDir().length());
        }
        for(AbstractBuild<?,?> build: project2.getBuilds()) {
            File file = new File(build.getRootDir(), "fileList");
            buildSizesProject2.put(build, getSize(readFileList(file)) + build.getRootDir().length());
        }
        BuildDiskUsageCalculationThread calculation = new BuildDiskUsageCalculationThread();
        if(calculation.isExecuting()) {
            waitUntilThreadEnds(calculation);
        }
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        for(AbstractBuild<?,?> build: buildSizesProject1.keySet()) {
            Long size = DiskUsageTestUtil.getBuildDiskUsageAction(build).getDiskUsage();
            assertEquals(buildSizesProject1.get(build), size, 0, "Build " + build.getNumber() + " of project " + build.getProject().getDisplayName() + " has wrong build size.");
        }
        for(AbstractBuild<?,?> build: buildSizesProject2.keySet()) {
            Long size = DiskUsageTestUtil.getBuildDiskUsageAction(build).getDiskUsage();
            assertEquals(buildSizesProject2.get(build), size, 0, "Build " + build.getNumber() + " of project " + build.getProject().getDisplayName() + " has wrong build size.");
        }

    }

    @Test
    @LocalData
    void testExecuteMatrixProject(JenkinsRule j) throws IOException, InterruptedException {
        // turn off run listener
        RunListener<?> listener = RunListener.all().get(DiskUsageBuildListener.class);
        j.jenkins.getExtensionList(RunListener.class).remove(listener);
        Map<AbstractBuild<?,?>, Long> buildSizesProject2 = new TreeMap<>();
        Map<String, Long> matrixConfigurationBuildsSize = new TreeMap<>();
        MatrixProject project = (MatrixProject) j.jenkins.getItem("project1");
        FreeStyleProject project2 = (FreeStyleProject) j.jenkins.getItem("project2");
        AbstractBuild<?,?> matrixBuild = project.getBuildByNumber(1);
        long matrixProjectBuildSize = getSize(readFileList(new File(matrixBuild.getRootDir(), "fileList"))) + matrixBuild.getRootDir().length();
        for(AbstractBuild<?,?> build: project2.getBuilds()) {
            File file = new File(build.getRootDir(), "fileList");
            buildSizesProject2.put(build, getSize(readFileList(file)) + build.getRootDir().length());
        }
        for(MatrixConfiguration c: project.getActiveConfigurations()) {
            AbstractBuild<?,?> build = c.getBuildByNumber(1);
            File file = new File(build.getRootDir(), "fileList");
            matrixConfigurationBuildsSize.put(c.getDisplayName(), getSize(readFileList(file)) + build.getRootDir().length());
        }
        BuildDiskUsageCalculationThread calculation = new BuildDiskUsageCalculationThread();
        if(calculation.isExecuting()) {
            waitUntilThreadEnds(calculation);
        }
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        Long size = DiskUsageTestUtil.getBuildDiskUsageAction(project.getBuildByNumber(1)).getDiskUsage();
        assertEquals(matrixProjectBuildSize, size, 0, "Build " + project.getBuildByNumber(1).getNumber() + " of project " + project.getDisplayName() + " has wrong build size.");
        for(AbstractBuild<?,?> build: buildSizesProject2.keySet()) {
            Long sizeFreeStyle = DiskUsageTestUtil.getBuildDiskUsageAction(build).getDiskUsage();
            assertEquals(buildSizesProject2.get(build), sizeFreeStyle, 0, "Build " + build.getNumber() + " of project " + build.getProject().getDisplayName() + " has wrong build size.");
        }
        for(MatrixConfiguration conf: project.getActiveConfigurations()) {
            AbstractBuild<?,?> build = conf.getBuildByNumber(1);
            assertEquals(matrixConfigurationBuildsSize.get(conf.getDisplayName()), DiskUsageTestUtil.getBuildDiskUsageAction(build).getDiskUsage(), 0, "Configuration " + conf.getDisplayName() + " has wrong build size for build 1.");
        }

    }

    @Test
    void testDoNotCalculateUnenabledDiskUsage(JenkinsRule j) throws Exception {
        FreeStyleProject projectWithoutDiskUsage = j.jenkins.createProject(FreeStyleProject.class, "projectWithoutDiskUsage");
        FreeStyleBuild build = projectWithoutDiskUsage.createExecutable();
        build.save();
        DiskUsageProjectActionFactory.DESCRIPTOR.disableBuildsDiskUsageCalculation();
        BuildDiskUsageCalculationThread calculation = AperiodicWork.all().get(BuildDiskUsageCalculationThread.class);
        calculation.execute(TaskListener.NULL);
        assertEquals(0l, DiskUsageTestUtil.getBuildDiskUsageAction(build).getAllDiskUsage(), 0, "Disk usage for build should not be counted.");
        DiskUsageProjectActionFactory.DESCRIPTOR.enableBuildsDiskUsageCalculation();
    }

    @Test
    void testDoNotExecuteDiskUsageWhenPreviousCalculationIsInProgress(JenkinsRule j) throws Exception {
        TestFreeStyleProject project = new TestFreeStyleProject(j.jenkins, "project");
        FreeStyleBuild build = new FreeStyleBuild(project);
        project.addBuild(build);
        j.jenkins.putItem(project);
        final BuildDiskUsageCalculationThread testCalculation = new BuildDiskUsageCalculationThread();
        Thread t = new Thread(){
            public void run() {
                try {
                    testCalculation.execute(TaskListener.NULL);
                } catch (IOException | InterruptedException ex) {
                    Logger.getLogger(JobDiskUsageCalculationThreadTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        t.start();
        Thread.sleep(1000);
        testCalculation.execute(TaskListener.NULL);
        assertEquals(0l, DiskUsageTestUtil.getBuildDiskUsageAction(project.getLastBuild()).getAllDiskUsage(), 0, "Disk usage should not start calculation if preview calculation is in progress.");
        t.interrupt();
    }

    public static class TestFreeStyleProject extends FreeStyleProject {

        public TestFreeStyleProject(ItemGroup group, String name) {
            super(group, name);
            onCreatedFromScratch();
        }

        @Override
        public File getBuildDir() {
            // is called during disk calculation, to be sure that calculation is in progress I make this operation longer
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                Logger.getLogger(JobDiskUsageCalculationThreadTest.class.getName()).log(Level.SEVERE, null, ex);
            }
            return super.getBuildDir();
        }

        public void addBuild(FreeStyleBuild build) {
            builds.put(build);
        }

        @Override
        public void save() {
            // do not want save
        }
    }

    @Test
    void testDoNotCalculateExcludedJobs(JenkinsRule j) throws Exception {
        FreeStyleProject excludedJob = j.jenkins.createProject(FreeStyleProject.class, "excludedJob");
        FreeStyleProject includedJob = j.jenkins.createProject(FreeStyleProject.class, "includedJob");
        List<String> excludes = new ArrayList<>();
        excludes.add(excludedJob.getName());
        DiskUsageProjectActionFactory.DESCRIPTOR.setExcludedJobs(excludes);
        j.buildAndAssertSuccess(excludedJob);
        j.buildAndAssertSuccess(includedJob);
        BuildDiskUsageCalculationThread calculation = AperiodicWork.all().get(BuildDiskUsageCalculationThread.class);
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        assertEquals(0l, DiskUsageTestUtil.getBuildDiskUsageAction(
            excludedJob.getLastBuild()).getAllDiskUsage(), 0, "Disk usage for excluded project should not be counted.");
        assertTrue(DiskUsageTestUtil.getBuildDiskUsageAction(includedJob.getLastBuild()).getAllDiskUsage() > 0, "Disk usage for excluded project should not be counted.");
        excludes.clear();
    }

    @Test
    @LocalData
    void testDoNotBreakLazyLoading(JenkinsRule j) throws IOException, InterruptedException {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.jenkins.getItem("project1");

        // method isBuilding() is used for determining disk usage and its calling load some builds
        project.isBuilding();
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        assertTrue(loadedBuilds < 8, "Test does not sense if there are all builds loaded, please rewrite it.");
        BuildDiskUsageCalculationThread calculation = AperiodicWork.all().get(BuildDiskUsageCalculationThread.class);
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        assertEquals(loadedBuilds, project._getRuns().getLoadedBuilds().size(), "Calculation of build disk usage should not cause loading of builds.");
    }
}
