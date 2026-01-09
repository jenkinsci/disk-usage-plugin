package hudson.plugins.disk_usage.project.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.disk_usage.BuildDiskUsageAction;
import hudson.plugins.disk_usage.project.DiskUsagePostBuildCalculation;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
public class DiskUsagePostBuildCalculationTest {

    @Test
    void testDiskUsageIsCalculated(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new DiskUsagePostBuildCalculation());
        j.buildAndAssertSuccess(project);
        AbstractBuild<?,?> build = project.getLastBuild();
        assertTrue(build.getAction(BuildDiskUsageAction.class).getDiskUsage() > 0, "Disk usage of build should be calculated.");

    }

    @Test
    void testDiskUsageIsNotCalculatedTwoTimes(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new DiskUsagePostBuildCalculation());
        j.buildAndAssertSuccess(project);
        AbstractBuild<?,?> build = project.getLastBuild();
        assertTrue(build.getLog(10).contains("Skipping calculation of disk usage, it was already done in post build step."), "Disk usage called by listener should be skipped.");
    }

    @Test
    void testDiskUsageCalculationForMatrixProject(JenkinsRule j) throws Exception {
        MatrixProject project = j.jenkins.createProject(MatrixProject.class, "project");
        project.getPublishersList().add(new DiskUsagePostBuildCalculation());
        j.buildAndAssertSuccess(project);
        AbstractBuild<?,?> build = project.getLastBuild();
        assertTrue(build.getAction(BuildDiskUsageAction.class).getDiskUsage() > 0, "Disk usage of build should be calculated.");
    }


}
