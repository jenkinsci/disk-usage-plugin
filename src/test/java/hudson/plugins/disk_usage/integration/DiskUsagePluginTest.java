/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import hudson.plugins.disk_usage.DiskUsagePlugin;
import hudson.plugins.disk_usage.DiskUsageProperty;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
public class DiskUsagePluginTest {

    @Test
    void testRefreshGlobalInformation(JenkinsRule j) throws IOException {
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        FreeStyleBuild build1 = project.createExecutable();
        FreeStyleBuild build2 = project.createExecutable();
        FreeStyleBuild build3 = project.createExecutable();
        Long sizeofBuild1 = 7546L;
        Long sizeofBuild2 = 6800L;
        Long sizeofBuild3 = 14032L;
        DiskUsageTestUtil.getBuildDiskUsageAction(build1).setDiskUsage(sizeofBuild1);
        DiskUsageTestUtil.getBuildDiskUsageAction(build2).setDiskUsage(sizeofBuild2);
        DiskUsageTestUtil.getBuildDiskUsageAction(build3).setDiskUsage(sizeofBuild3);
        DiskUsagePlugin plugin = j.jenkins.getPlugin(DiskUsagePlugin.class);
        long workspaceUsage = 20345L;
        long jobUsage = 5980L;
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            property = new DiskUsageProperty();
            project.addProperty(property);
        }
        property.setDiskUsageWithoutBuilds(jobUsage);
        property.putAgentWorkspaceSize(j.jenkins, j.jenkins.getWorkspaceFor((TopLevelItem) project).getRemote(), workspaceUsage);
        plugin.refreshGlobalInformation();
        assertEquals(sizeofBuild1 + sizeofBuild2 + sizeofBuild3, plugin.getCashedGlobalBuildsDiskUsage(), 0, "Global build diskUsage should be refreshed.");
        assertEquals(jobUsage, plugin.getCashedGlobalJobsWithoutBuildsDiskUsage(), 0, "Global job diskUsage should be refreshed.");
        assertEquals(workspaceUsage, plugin.getCashedGlobalWorkspacesDiskUsage(), 0, "Global workspace diskUsage should be refreshed.");

    }

    @Test
    @LocalData
    void testNotBreakLazyLoading(JenkinsRule j) throws IOException {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.jenkins.getItem("project1");
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        assertTrue(8 > loadedBuilds, "This tests does not sense if there are loaded all builds.");
        j.jenkins.getPlugin(DiskUsagePlugin.class).refreshGlobalInformation();
        assertEquals(47000, j.jenkins.getPlugin(DiskUsagePlugin.class).getCashedGlobalBuildsDiskUsage(), 0, "Size of builds should be loaded.");
        assertTrue(loadedBuilds <= project._getRuns().getLoadedBuilds().size(), "No new build should be loaded.");
    }

    @Test
    @LocalData
    void testDoNotLoadAllBuildsDuringStart(JenkinsRule j) {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.jenkins.getItem("project1");
        AbstractProject<?,?> project2 = (AbstractProject<?,?>) j.jenkins.getItem("project2");
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        assertEquals(0, loadedBuilds, "Builds of project with disk-usage.xml should not be loaded.");
        loadedBuilds = project2._getRuns().getLoadedBuilds().size();
        assertEquals(0, loadedBuilds, "Builds of project without disk-usage.xml should not be loaded.");
    }

    @Test
    @LocalData
    void testDoLoadBuildInformationWhenBuildIsLoaded(JenkinsRule j) {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.jenkins.getItem("project1");
        AbstractBuild<?,?> build = project.getBuild("1");
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertNotNull(property.getDiskUsageOfBuild(2), "Build should be add after its loading (if it is not present before).");
        assertEquals(1, loadedBuilds, "Only required build should be loaded into Jenkins.");
    }

    @Test
    @LocalData
    void testBuildInfoIsNoLoadedMultipleTimes(JenkinsRule j) throws Exception {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.jenkins.getItem("project1");
        AbstractBuild<?,?> build = project.getBuild("2");
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        j.jenkins.reload();
        project = (AbstractProject<?,?>) j.jenkins.getItem("project1");
        build = project.getBuild("2");
        property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertNotNull(property.getDiskUsageBuildInformation(2), "Should be loaded build 2");
        assertEquals(1, property.getDiskUsageOfBuilds().size(), "Only one build should be loaded into disk usage build information.");

    }
}
