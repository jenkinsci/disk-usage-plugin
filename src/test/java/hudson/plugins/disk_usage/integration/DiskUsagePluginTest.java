/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import hudson.model.AbstractBuild;
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
    
    @Test
    @LocalData
    public void testDoNotLoadAllBuildsDuringStart(){
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        AbstractProject project2 = (AbstractProject) j.jenkins.getItem("project2");
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        assertEquals("Builds of project with disk-usage.xml should not be loaded.", 0, loadedBuilds);
        loadedBuilds = project2._getRuns().getLoadedBuilds().size();
        assertEquals("Builds of project without disk-usage.xml should not be loaded.", 0, loadedBuilds);
    }
    
    @Test
    @LocalData
    public void testDoLoadBuildInformationWhenBuildIsLoaded(){
       AbstractProject project = (AbstractProject) j.jenkins.getItem("project1"); 
       AbstractBuild build = project.getBuild("1");
       int loadedBuilds = project._getRuns().getLoadedBuilds().size();
       DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
       assertNotNull("Build should be add after its loading (if it is not present before).", property.getDiskUsageOfBuild(2));
       assertEquals("Only required build should be loaded into Jenkins.", 1, loadedBuilds);
    }
    
    @Test
    @LocalData    
    public void testBuildInfoIsNoLoadedMultipleTimes() throws Exception{
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        AbstractBuild build = project.getBuild("2");
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        j.jenkins.reload();
        project = (AbstractProject) j.jenkins.getItem("project1");
        build = project.getBuild("2");
        property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertNotNull("Should be loaded build 2", property.getDiskUsageBuildInformation(2));
        assertEquals("Only one build should be loaded into disk usage build information.", 1, property.getDiskUsageOfBuilds().size());
        
    }
}
