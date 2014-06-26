/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import hudson.tasks.Shell;
import java.io.File;
import hudson.FilePath;
import hudson.plugins.disk_usage.DiskUsageProperty;
import hudson.model.AbstractProject;
import org.junit.Test;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Rule;
import static org.junit.Assert.*;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageBuildListenerTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void testOnDeleted() throws Exception{
        AbstractProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        project.getBuildByNumber(2).delete();
        assertNull("Build 2 was not removed from caches informations.", property.getDiskUsageBuildInformation(2));
        assertNotNull("Disk usage property whoud contains cashed information about build 1.", property.getDiskUsageOfBuild(1));
        assertNotNull("Disk usage property whoud contains cashed information about build 3.", property.getDiskUsageOfBuild(3));
    }
    
    @Test
    public void testOnCompleted() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo ahoj > log.log"));
        j.buildAndAssertSuccess(project);
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertNotNull("Build information is cached.", property.getDiskUsageBuildInformation(1));
        assertTrue("Build disk usage should be counted.", property.getDiskUsageOfBuild(1)>0);
        assertTrue("Workspace of build should be counted.", property.getAllWorkspaceSize()>0);
    }
}
