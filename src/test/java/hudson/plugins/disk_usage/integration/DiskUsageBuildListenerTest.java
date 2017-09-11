/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.Items;
import hudson.model.listeners.RunListener;
import hudson.plugins.disk_usage.BuildDiskUsageAction;
import hudson.plugins.disk_usage.DiskUsageBuildListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;

import hudson.FilePath;
import hudson.plugins.disk_usage.DiskUsageProperty;
import hudson.model.AbstractProject;
import jenkins.model.lazy.BuildReference;
import org.junit.*;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.junit.Assert.*;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageBuildListenerTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @BeforeClass
    public static void setEnvironmentVariables() throws IOException {
        System.setProperty("jenkins.model.lazy.BuildReference.MODE", "weak");

        System.setProperty("jenkins.test.timeout","10000");
        System.setProperty("maven.surefire.debug","10000");
       // j.jenkins.getGlobalNodeProperties().add(prop);
    }
    
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
        j.timeout = 10000;
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo ahoj > log.log"));
        j.buildAndAssertSuccess(project);
        DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
        assertNotNull("Build information is cached.", property.getDiskUsageBuildInformation(1));
        assertTrue("Build disk usage should be counted.", property.getDiskUsageOfBuild(1)>0);
        assertTrue("Workspace of build should be counted.", property.getAllWorkspaceSize()>0);
    }


    @Issue("JENKINS-33219")
    @Test(timeout = 10000000L)
    @LocalData
    public void testOnLoadCauseDeadLock() throws Exception {
        j.timeout = 10000;
        BuildReference.DefaultHolderFactory f = new BuildReference.DefaultHolderFactory();

       FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem("job");
       final File file = p.getConfigFile().getFile().getParentFile();


        System.err.println(p.getConfigFile().getFile().exists() + " " + p.getConfigFile().getFile().getAbsolutePath());
       // j.buildAndAssertSuccess(project);
        p.removeProperty(DiskUsageProperty.class);
        final FreeStyleBuild build = p.getLastBuild();
        System.err.println("holder " + f.make(build).getClass() + " builds is " + p.getBuilds().toArray().length);

        p = (FreeStyleProject) j.jenkins.getItem("job");
        System.err.println("loaded builds " + p._getRuns().getLoadedBuilds());
        final DiskUsageBuildListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        Thread onLoad = new Thread(){

            public void run(){
                FreeStyleProject project = (FreeStyleProject) j.jenkins.getItem("job");
                int count = 0;
                while(count<50){
                    count++;
                    project = (FreeStyleProject) j.jenkins.getItem("job");
                    BuildDiskUsageAction action = new BuildDiskUsageAction(project.getBuildByNumber(106));
                    System.err.println("loadedBuilds " + project._getRuns().getLoadedBuilds());
                    try {


                        project = (FreeStyleProject) Items.load(j.jenkins, file);
                        synchronized(j.jenkins) {
                            j.jenkins.remove(project);
                            j.jenkins.putItem(project);
                        }
                        project.getProperty(DiskUsageProperty.class).getDiskUsage().getConfigFile().delete();
                        project.removeProperty(DiskUsageProperty.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    System.err.println("thread onLoad done, cycle left " + count + " loaded builds " + project._getRuns().getLoadedBuilds());


                }


            }
        };
        Thread nextBuildNumber = new Thread(){

            public void run(){

                int count = 0;
                FreeStyleProject project = (FreeStyleProject) j.jenkins.getItem("job");
                while(count<50) {
                    count++;
                    try {
                        project = (FreeStyleProject) j.jenkins.getItem("job");
                        project.updateNextBuildNumber(107 + count);
                        project = (FreeStyleProject) Items.load(j.jenkins, file);
                        synchronized (j.jenkins) {
                            j.jenkins.remove(project);
                            j.jenkins.putItem(project);
                        }
                        project.getProperty(DiskUsageProperty.class).getDiskUsage().getConfigFile().delete();
                        project.removeProperty(DiskUsageProperty.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.err.println("loadedBuilds2 " + project._getRuns().getLoadedBuilds());

                    System.err.println("thread nextBuildNumber done, cycle left " + count + " loaded builds " + project._getRuns().getLoadedBuilds());
                }

            }
        };
        onLoad.start();
        nextBuildNumber.start();
        onLoad.join();
        nextBuildNumber.join();
    }
}
