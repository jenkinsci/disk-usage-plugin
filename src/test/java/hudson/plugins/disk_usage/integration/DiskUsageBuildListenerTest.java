/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Functions;
import hudson.model.FreeStyleProject;
import hudson.plugins.disk_usage.DiskUsageProperty;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
public class DiskUsageBuildListenerTest {

    @Test
    void testOnDeleted(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        project.getBuildByNumber(2).delete();
        assertNull(property.getDiskUsageBuildInformation(2), "Build 2 was not removed from caches informations.");
        assertNotNull(property.getDiskUsageOfBuild(1), "Disk usage property whoud contains cashed information about build 1.");
        assertNotNull(property.getDiskUsageOfBuild(3), "Disk usage property whoud contains cashed information about build 3.");
    }

    @Test
    void testOnCompleted(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        if(Functions.isWindows()) {
            project.getBuildersList().add(new BatchFile("echo ahoj > log.log"));
        } else {
            project.getBuildersList().add(new Shell("echo ahoj > log.log"));
        }
        j.buildAndAssertSuccess(project);
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        assertNotNull(property.getDiskUsageBuildInformation(1), "Build information is cached.");
        assertTrue(property.getDiskUsageOfBuild(1) > 0, "Build disk usage should be counted.");
        assertTrue(property.getAllWorkspaceSize() > 0, "Workspace of build should be counted.");
    }
}
