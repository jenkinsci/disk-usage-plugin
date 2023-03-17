package hudson.plugins.disk_usage.project.integration;

import static org.junit.Assert.assertTrue;

import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.disk_usage.BuildDiskUsageAction;
import hudson.plugins.disk_usage.project.DiskUsagePostBuildCalculation;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsagePostBuildCalculationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testDiskUsageIsCalculated() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new DiskUsagePostBuildCalculation());
        j.buildAndAssertSuccess(project);
        AbstractBuild<?,?> build = project.getLastBuild();
        assertTrue("Disk usage of build should be calculated.", build.getAction(BuildDiskUsageAction.class).getDiskUsage() > 0);

    }

    @Test
    public void testDiskUsageIsNotCalculatedTwoTimes() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new DiskUsagePostBuildCalculation());
        j.buildAndAssertSuccess(project);
        AbstractBuild<?,?> build = project.getLastBuild();
        assertTrue("Disk usage called by listener should be skipped.", build.getLog(10).contains("Skipping calculation of disk usage, it was already done in post build step."));
    }

    @Test
    public void testDiskUsageCalculationForMatrixProject() throws Exception {
        MatrixProject project = j.jenkins.createProject(MatrixProject.class, "project");
        project.getPublishersList().add(new DiskUsagePostBuildCalculation());
        j.buildAndAssertSuccess(project);
        AbstractBuild<?,?> build = project.getLastBuild();
        assertTrue("Disk usage of build should be calculated.", build.getAction(BuildDiskUsageAction.class).getDiskUsage() > 0);
    }


}
