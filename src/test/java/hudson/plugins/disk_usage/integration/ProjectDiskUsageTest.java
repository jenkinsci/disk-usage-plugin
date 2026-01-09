/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AbstractProject;
import hudson.plugins.disk_usage.DiskUsageBuildInformation;
import hudson.plugins.disk_usage.DiskUsageProperty;
import hudson.plugins.disk_usage.ProjectDiskUsageAction;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author lucinka
 */
@WithJenkins
public class ProjectDiskUsageTest {

    @Test
    @LocalData
    void testAllInfoLoaded(JenkinsRule j) throws IOException {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.jenkins.getItem("project1");
        ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        Set<DiskUsageBuildInformation> informations = project.getAction(ProjectDiskUsageAction.class).getBuildsInformation();
        assertEquals(loadedBuilds, project._getRuns().getLoadedBuilds().size(), "Number of loaded builds should be the same.");
        assertEquals(8, informations.size(), "Set of DisUsageBuildInformation does not contains all builds of job.");
        assertTrue(8 > loadedBuilds, "The test have to be rewritten because loaded builds is not less then all builds.");
    }

    @Test
    @LocalData
    void testFirstLoad(JenkinsRule j) throws IOException {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.jenkins.getItem("project1");
        Set<DiskUsageBuildInformation> informations = project.getProperty(DiskUsageProperty.class)
                                                             .getDiskUsage().getBuildDiskUsage(false);
        assertEquals(0, informations.size(), "Set of DisUsageBuildInformation should not contain information about builds because they are not loaded.");
    }

    @Test
    @LocalData
    void testLoadingAllBuildInformationFromPreviousVersion(JenkinsRule j) {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.jenkins.getItem("project1");
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        assertEquals(8, property.getDiskUsage().getBuildDiskUsage(true).size(), 0, "Builds information should be loaded.");
    }
}
