/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import com.github.olivergondza.dumpling.factory.JvmRuntimeFactory;
import com.github.olivergondza.dumpling.model.jvm.JvmThread;
import com.github.olivergondza.dumpling.model.jvm.JvmThreadSet;
import com.github.olivergondza.dumpling.query.BlockingTree;
import com.github.olivergondza.dumpling.query.Deadlocks;
import com.thoughtworks.xstream.mapper.Mapper;
import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.Items;
import hudson.model.listeners.RunListener;
import hudson.plugins.disk_usage.BuildDiskUsageAction;
import hudson.plugins.disk_usage.DiskUsageBuildListener;
import hudson.plugins.disk_usage.DiskUsageUtil;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Logger;

import hudson.FilePath;
import hudson.plugins.disk_usage.DiskUsageProperty;
import hudson.model.AbstractProject;
import jenkins.model.Jenkins;
import jenkins.model.lazy.BuildReference;
import org.apache.tools.mail.ErrorInQuitException;
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

        System.setProperty("jenkins.test.timeout", "10000");
        System.setProperty("maven.surefire.debug", "10000");
        // j.jenkins.getGlobalNodeProperties().add(prop);
    }

    @Test
    public void testOnDeleted() throws Exception {
        AbstractProject project = j.createFreeStyleProject();
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        DiskUsageProperty property = DiskUsageUtil.getDiskUsageProperty(project);
        project.getBuildByNumber(2).delete();
        assertNull("Build 2 was not removed from caches informations.", property.getDiskUsageBuildInformation(2));
        assertNotNull("Disk usage property whoud contains cashed information about build 1.", property.getDiskUsageOfBuild(1));
        assertNotNull("Disk usage property whoud contains cashed information about build 3.", property.getDiskUsageOfBuild(3));
    }

    @Test
    public void testOnCompleted() throws Exception {
        j.timeout = 10000;
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo ahoj > log.log"));
        j.buildAndAssertSuccess(project);
        DiskUsageProperty property = DiskUsageUtil.getDiskUsageProperty(project);
        assertNotNull("Build information is cached.", property.getDiskUsageBuildInformation(1));
        assertTrue("Build disk usage should be counted.", property.getDiskUsageOfBuild(1) > 0);
        assertTrue("Workspace of build should be counted.", property.getAllWorkspaceSize() > 0);
    }


    @Issue("JENKINS-33219")
    @Test(timeout = 700000L)
    @LocalData
    public void testOnLoadCauseDeadLock() throws Exception {
        // it is necessary to call functions with a lot of IO operation many times in cycle, so the test can take little longer
        j.timeout = 700;
        AddNewProperty onLoad = new AddNewProperty(j.jenkins);
        UpdateNexBuildNumber nextBuildNumber = new UpdateNexBuildNumber(j.jenkins);
        onLoad.start();
        nextBuildNumber.start();
        while(!onLoad.isFinished || !nextBuildNumber.isFinished) {
            Deadlocks deadlocks = new Deadlocks();
            Set<JvmThreadSet> set = deadlocks.query(new JvmRuntimeFactory().currentRuntime().getThreads()).getDeadlocks();
            if(!set.isEmpty()) {
                System.err.println("Deadlock was detected:");
                for(JvmThreadSet s : set) {
                    System.err.println(s);
                }
                fail("Deadlock was detected.");
            }
            Thread.sleep(6000);
        }
    }



    public static class UpdateNexBuildNumber extends Thread {
        public boolean isFinished = false;
        public static String name = "Add new property";
        private Jenkins jenkins;

        public UpdateNexBuildNumber(Jenkins jenkins) {
            super(name);
            this.jenkins = jenkins;
        }

        public void run() {
            int count = 0;
            while (count < 100) {
                try {
                    //get project without DiskUsageProperty on without loaded builds
                    FreeStyleProject project = DiskUsageTestUtil.prepareProjet(jenkins,"job");
                    project.getLazyBuildMixIn().getBuildByNumber(55);
                } catch (Exception e) {
                    if(e instanceof NullPointerException){
                        continue;
                    }
                    else {
                        e.printStackTrace();
                    }
                } catch (Error e) {
                    e.printStackTrace();
                }
                count++;
            }
            isFinished = true;

        }

    }

    public static class AddNewProperty extends Thread {

        public boolean isFinished = false;
        public static String name = "Update next build number";
        private Jenkins jenkins;

        public AddNewProperty(Jenkins jenkins) {
            super(name);
            this.jenkins = jenkins;
        }

        public void run() {
            int count = 0;
            while (count < 100) {
                try {
                    //get project without DiskUsageProperty on without loaded builds
                    FreeStyleProject project = DiskUsageTestUtil.prepareProjet(jenkins,"job");
                    project.updateNextBuildNumber(107 + count);

                } catch (Exception e) {
                    if(e instanceof NullPointerException){
                        continue;
                    }
                    else {
                        e.printStackTrace();
                    }
                } catch (Error e) {
                    e.printStackTrace();
                }
                count++;
            }
            isFinished = true;
        }
    }


}
