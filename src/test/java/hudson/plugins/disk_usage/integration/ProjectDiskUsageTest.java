/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import hudson.plugins.disk_usage.*;
import hudson.model.AbstractProject;
import hudson.plugins.disk_usage.DiskUsageBuildInformation;
import hudson.plugins.disk_usage.ProjectDiskUsageAction;
import java.io.IOException;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import static org.junit.Assert.*;

/**
 *
 * @author lucinka
 */
public class ProjectDiskUsageTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    
    @Test
    @LocalData
    public void testAllInfoLoaded() throws IOException{
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        Set<DiskUsageBuildInformation> informations = project.getAction(ProjectDiskUsageAction.class).getBuildsInformation();
        assertEquals("Number of loaded builds should be the same.", loadedBuilds, project._getRuns().getLoadedBuilds().size());
        assertEquals("Set of DisUsageBuildInformation does not contains all builds of job.", 8, informations.size());
        assertTrue("The test have to be rewritten because loaded builds is not less then all builds.", 8 > loadedBuilds);
    }
    
    @Test
    @LocalData
    public void testFirstLoad() throws IOException{
        AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
        Set<DiskUsageBuildInformation> informations = ((DiskUsageProperty)project.getProperty(DiskUsageProperty.class)).getDiskUsage().getBuildDiskUsage(false);
        assertEquals("Set of DisUsageBuildInformation should not contain information about builds because they are not loaded.", 0, informations.size());
    }
    
    @Test
    @LocalData
    public void testLoadingAllBuildInformationFromPreviousVersion(){
       AbstractProject project = (AbstractProject) j.jenkins.getItem("project1");
       DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
       assertEquals("Builds information should be loaded.", 8, property.getDiskUsage().getBuildDiskUsage(true).size(), 0);
    }
}
