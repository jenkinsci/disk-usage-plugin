package hudson.plugins.disk_usage.integration;

import hudson.XmlFile;
import hudson.plugins.disk_usage.DiskUsagePlugin;
import hudson.plugins.disk_usage.DiskUsageProjectActionFactory;
import hudson.plugins.disk_usage.configuration.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.IOException;

/**
 * Created by lvotypko on 10/25/17.
 */
public class DiskUsageProjectActionFactoryTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();


    @Test
    @LocalData
    public void testBackwardCompatibility() throws IOException {
        DiskUsageProjectActionFactory.DescriptorImpl descriptor = rule.jenkins.getPlugin(DiskUsagePlugin.class).getConfiguration();
        XmlFile file = new XmlFile(new File(rule.jenkins.getRootDir(), descriptor.getId() + ".xml"));
        System.err.println(file.asString());
        assertTrue(descriptor.getType()== GlobalConfiguration.ConfigurationType.CUSTOM);
        assertTrue(descriptor.isCalculationJobsEnabled());
        assertTrue(descriptor.isCalculationBuildsEnabled());
        assertFalse(descriptor.isCalculationNotUsedDataEnabled());
        assertTrue(descriptor.isCalculationWorkspaceEnabled());
        assertTrue(descriptor.getConfiguration().getJobConfiguration().areBuilsCalculatedSeparately());
        assertTrue(descriptor.getConfiguration().getJobConfiguration().getBuildConfiguration().areBuildsRecalculated());
        assertTrue(descriptor.getCheckWorkspaceOnAgent());
        assertTrue(descriptor.warnAboutAllJobsExceetedSize());
        assertTrue(descriptor.warnAboutBuildExceetedSize());
        assertTrue(descriptor.warnAboutJobExceetedSize());
        assertTrue(descriptor.warnAboutJobWorkspaceExceedSize());
        assertTrue(descriptor.getShowFreeSpaceForJobDirectory());
        assertEquals(descriptor.getEmailAddress(),"somebody@something.com");
        assertEquals(descriptor.getHistoryLength(),180);
        assertEquals(descriptor.getTimeoutWorkspace(),6);
        assertEquals(descriptor.getJobExceedSizeInString(),"2 GB");
        assertEquals(descriptor.getBuildExceedSizeInString(),"100 MB");
        assertEquals(descriptor.getJobWorkspaceExceedSizeInString(),"30 GB");
        assertEquals(descriptor.getAllJobsExceedSizeInString(),"145 GB");
        assertEquals(descriptor.getCountIntervalForBuilds(),"0 */6 * * *");
        assertEquals(descriptor.getCountIntervalForJobs(),"0 */6 * * *");
        assertEquals(descriptor.getCountIntervalForWorkspaces(),"0 */6 * * *");
        assertNull(descriptor.getCountIntervalForNotUsedData());
        assertEquals(descriptor.getExcludedJobsInString(),"job");
    }
}
