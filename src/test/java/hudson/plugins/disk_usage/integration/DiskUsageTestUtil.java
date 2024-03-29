/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.integration;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.plugins.disk_usage.BuildDiskUsageAction;
import hudson.plugins.disk_usage.DiskUsageCalculation;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageTestUtil {
    protected static List<File> readFileList(File file) throws FileNotFoundException, IOException {
        List<File> files = new ArrayList<>();
        String path = file.getParentFile().getAbsolutePath();
        BufferedReader content = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String line = content.readLine();
        while(line != null) {
            files.add(new File(path + "/" + line));
            line = content.readLine();
        }
        return files;
    }

    protected static Long getSize(List<File> files) {
        long length = 0L;
        for(File file: files) {
            length += file.length();
        }
        return length;
    }

    protected static Slave createAgent(String name, String remoteFS, Jenkins jenkins, ComputerLauncher launcher) throws Exception {
        DumbSlave agent = new DumbSlave(name, "dummy",
                                        remoteFS, "2", Mode.NORMAL, "", launcher,
                                        RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        jenkins.addNode(agent);
        while(agent.toComputer() == null || !agent.toComputer().isOnline()) {
            Thread.sleep(100);
        }
        return agent;
    }

    protected static BuildDiskUsageAction getBuildDiskUsageAction(AbstractBuild<?,?> build) {
        for(Action a: build.getAllActions()) {
            if(a instanceof BuildDiskUsageAction) {
                return (BuildDiskUsageAction) a;
            }
        }
        return null;
    }

    protected static void cancelCalculation(DiskUsageCalculation calculation) {
        for(Thread t: Thread.getAllStackTraces().keySet()) {
            if(t.getName().equals(calculation.getThreadName())) {
                t.interrupt();
                return;
            }
        }
    }

}
