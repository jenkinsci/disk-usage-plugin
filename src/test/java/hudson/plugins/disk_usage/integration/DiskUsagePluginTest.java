/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import hudson.model.AbstractProject;
import org.jvnet.hudson.test.recipes.LocalData;
import hudson.plugins.disk_usage.*;
import org.junit.Test;
import hudson.model.TopLevelItem;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.io.IOException;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsagePluginTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void testRefreshGlobalInformation() throws IOException{
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        FreeStyleBuild build1 = project.createExecutable();
        FreeStyleBuild build2 = project.createExecutable();
        FreeStyleBuild build3 = project.createExecutable();
        Long sizeofBuild1 = 7546l;
        Long sizeofBuild2 = 6800l;
        Long sizeofBuild3 = 14032l;
        DiskUsageTestUtil.getBuildDiskUsageAction(build1).setDiskUsage(sizeofBuild1);
        DiskUsageTestUtil.getBuildDiskUsageAction(build2).setDiskUsage(sizeofBuild2);
        DiskUsageTestUtil.getBuildDiskUsageAction(build3).setDiskUsage(sizeofBuild3);
        DiskUsagePlugin plugin = j.jenkins.getPlugin(DiskUsagePlugin.class);
        Long workspaceUsage = 20345l;
        Long jobUsage = 5980l;
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property==null){
            property = new DiskUsageProperty();
            project.addProperty(property);
        }
        property.setDiskUsageWithoutBuilds(jobUsage);
        property.putSlaveWorkspaceSize(j.jenkins, j.jenkins.getWorkspaceFor((TopLevelItem)project).getRemote(), workspaceUsage);
        plugin.refreshGlobalInformation();
        assertEquals("Global build diskUsage should be refreshed.", sizeofBuild1 + sizeofBuild2 +sizeofBuild3, plugin.getCashedGlobalBuildsDiskUsage(), 0);
        assertEquals("Global job diskUsage should be refreshed.", jobUsage, plugin.getCashedGlobalJobsWithoutBuildsDiskUsage(), 0);
        assertEquals("Global workspace diskUsage should be refreshed.", workspaceUsage, plugin.getCashedGlobalWorkspacesDiskUsage(), 0);
          
    }
    
    @Test
    @LocalData
    public void testNotBreakLazyLoading() throws IOException{
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        assertTrue("This tests does not sense if there are loaded all builds.",8>loadedBuilds);
        j.jenkins.getPlugin(DiskUsagePlugin.class).refreshGlobalInformation();
        assertEquals("Size of builds should be loaded.", 47000, j.jenkins.getPlugin(DiskUsagePlugin.class).getCashedGlobalBuildsDiskUsage(), 0);
        assertTrue("No new build should be loaded.", loadedBuilds <= project._getRuns().getLoadedBuilds().size());
    }
}
