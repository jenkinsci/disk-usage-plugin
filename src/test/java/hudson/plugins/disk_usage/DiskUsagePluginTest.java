/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

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
 * @author lucinka
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
        build1.addAction(new BuildDiskUsageAction(build1, sizeofBuild1));
        build2.addAction(new BuildDiskUsageAction(build2, sizeofBuild2));
        build3.addAction(new BuildDiskUsageAction(build3, sizeofBuild3));
        DiskUsagePlugin plugin = j.jenkins.getPlugin(DiskUsagePlugin.class);
        Long workspaceUsage = 20345l;
        Long jobUsage = 5980l;
        DiskUsageProperty property = new DiskUsageProperty();
        project.addProperty(property);
        property.setDiskUsageWithoutBuilds(jobUsage);
        property.putSlaveWorkspaceSize(j.jenkins, j.jenkins.getWorkspaceFor((TopLevelItem)project).getRemote(), workspaceUsage);
        plugin.refreshGlobalInformation();
        assertEquals("Global build diskUsage should be refreshed.", sizeofBuild1 + sizeofBuild2 +sizeofBuild3, plugin.getCashedGlobalBuildsDiskUsage(), 0);
        assertEquals("Global job diskUsage should be refreshed.", jobUsage, plugin.getCashedGlobalJobsWithoutBuildsDiskUsage(), 0);
        assertEquals("Global workspace diskUsage should be refreshed.", workspaceUsage, plugin.getCashedGlobalWorkspacesDiskUsage(), 0);
          
    }
}
