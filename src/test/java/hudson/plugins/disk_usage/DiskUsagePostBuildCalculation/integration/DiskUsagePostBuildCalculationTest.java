package hudson.plugins.disk_usage.DiskUsagePostBuildCalculation.integration;

import hudson.plugins.disk_usage.project.DiskUsagePostBuildCalculation;
import hudson.plugins.disk_usage.integration.*;
import hudson.model.Action;
import java.io.IOException;
import java.util.List;
import hudson.plugins.disk_usage.BuildDiskUsageAction;
import org.junit.Test;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.matrix.AxisList;
import hudson.matrix.TextAxis;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Rule;
import static org.junit.Assert.*;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsagePostBuildCalculationTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void testDiskUsageIsCalculated() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new DiskUsagePostBuildCalculation());
        j.buildAndAssertSuccess(project);
        AbstractBuild build = project.getLastBuild();
        assertTrue("Disk usage of build should be calculated.", build.getAction(BuildDiskUsageAction.class).getDiskUsage() > 0);
        
    }
    
    @Test
    public void testDiskUsageIsNotCalculatedTwoTimes() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new DiskUsagePostBuildCalculation());
        j.buildAndAssertSuccess(project);
        AbstractBuild build = project.getLastBuild();
        assertTrue("Disk usage called by listener should be skipped.", build.getLog(10).contains("Skipping calculation of disk usage"));
    }
    
    
}
