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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageTestUtil {
    protected static List<File> readFileList(File file) throws FileNotFoundException, IOException{
        List<File> files = new ArrayList<File>();
        String path = file.getParentFile().getAbsolutePath();
        BufferedReader content = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String line = content.readLine();
        while(line!=null){
            files.add(new File(path + "/" + line));
            line = content.readLine();
        }
        return files;
    }
    
    protected static Long getSize(List<File> files){
        Long lenght = 0l;
        for(File file: files){
            lenght += file.length();
        }
        return lenght;
    }
    
    protected static Slave createSlave(String name, String remoteFS, Jenkins jenkins, ComputerLauncher launcher) throws Exception{
        DumbSlave slave = new DumbSlave(name, "dummy",
            remoteFS, "2", Mode.NORMAL, "", launcher,
            RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
    	jenkins.addNode(slave);
        while(slave.toComputer()==null || !slave.toComputer().isOnline()){
            Thread.sleep(100);
        }
        return slave;
    }
    
    protected static BuildDiskUsageAction getBuildDiskUsageAction(AbstractBuild build){
        for(Action a : build.getAllActions()){
            if(a instanceof BuildDiskUsageAction) {
                return (BuildDiskUsageAction) a;
            }
        }
        return null;
    }
    
    protected static void cancelCalculation(DiskUsageCalculation calculation){
       for(Thread t : Thread.getAllStackTraces().keySet()){
           if(t.getName().equals(calculation.getThreadName())){
               t.interrupt();
               return;
           }
       } 
    }
    
    protected static void createFileWithContent(File file) throws FileNotFoundException{
        file.getParentFile().mkdirs();
        PrintStream stream = new PrintStream(file);
        stream.println("hello");
        stream.close();
    }
}
