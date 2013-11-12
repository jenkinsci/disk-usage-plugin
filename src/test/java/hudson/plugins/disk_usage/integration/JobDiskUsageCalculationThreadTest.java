package hudson.plugins.disk_usage.integration;

import hudson.plugins.disk_usage.*;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AperiodicWork;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.util.RunList;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import org.mockito.stubbing.Answer;

/**
 *
 * @author Lucie Votypkova
 */
public class JobDiskUsageCalculationThreadTest extends HudsonTestCase{
    
    private void waitUntilThreadEnds(JobWithoutBuildsDiskUsageCalculation calculation) throws InterruptedException{
        while(calculation.isExecuting()){          
            Thread.sleep(100);
        }
    }
    
     private List<File> readFileList(File file) throws FileNotFoundException, IOException{
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
    
    private Long getSize(List<File> files){
        Long length = 0l;
        for(File file: files){
            length += file.length();
        }
        return length; 
    }
    
    @Test
    @LocalData
    public void testExecute() throws IOException, InterruptedException{
        //turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        jenkins.getExtensionList(RunListener.class).remove(listener);
        FreeStyleProject project = (FreeStyleProject) jenkins.getItem("project1");
        FreeStyleProject project2 = (FreeStyleProject) jenkins.getItem("project2");
        File file = new File(project.getRootDir(),"fileList");
        Long projectSize = getSize(readFileList(file)) + project.getRootDir().length();
        file = new File(project2.getRootDir(),"fileList");
        Long project2Size = getSize(readFileList(file)) + project2.getRootDir().length();
        JobWithoutBuildsDiskUsageCalculation calculation = new JobWithoutBuildsDiskUsageCalculation();
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        assertEquals("Project project has wrong job size.", projectSize, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
        assertEquals("Project project2 has wrong job size.", project2Size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
    }
    
    @Test
    @LocalData
    public void testMatrixProject() throws IOException, InterruptedException{
        //turn off run listener
        RunListener listener = RunListener.all().get(DiskUsageBuildListener.class);
        jenkins.getExtensionList(RunListener.class).remove(listener);
        Map<String,Long> matrixConfigurationsSize = new TreeMap<String,Long>();
        MatrixProject project = (MatrixProject) jenkins.getItem("project1");
        FreeStyleProject project2 = (FreeStyleProject) jenkins.getItem("project2");
        File file = new File(project.getRootDir(),"fileList");
        Long projectSize = getSize(readFileList(file)) + project.getRootDir().length();
        file = new File(project2.getRootDir(),"fileList");
        Long project2Size = getSize(readFileList(file)) + project2.getRootDir().length();
        for(MatrixConfiguration config: project.getItems()){
            File f = new File(config.getRootDir(),"fileList");
            Long size = getSize(readFileList(f)) + config.getRootDir().length();
            matrixConfigurationsSize.put(config.getDisplayName(), size);
        }
        JobWithoutBuildsDiskUsageCalculation calculation = new JobWithoutBuildsDiskUsageCalculation();
        calculation.execute(TaskListener.NULL);
        waitUntilThreadEnds(calculation);
        assertEquals("Project project has wrong job size.", projectSize, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
        assertEquals("Project project2 has wrong job size.", project2Size, project2.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);
        for(MatrixConfiguration config: project.getItems()){
            assertEquals("Configuration " + config.getDisplayName() + " has wrong job size.", matrixConfigurationsSize.get(config.getDisplayName()), config.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds(), 0);           
        }
    }
    
    public void testDoNotExecuteDiskUsageWhenPreviousCalculationIsInProgress() throws Exception{
        FreeStyleProject project = new FreeStyleProject(jenkins, "execution"){
            @Override
           public RunList<FreeStyleBuild> getBuilds(){
                //is called during disk calculation, to be sure that calculation is in progress I make this operation longer
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(JobDiskUsageCalculationThreadTest.class.getName()).log(Level.SEVERE, null, ex);
                }
               return new RunList<FreeStyleBuild>();
           }
            
            @Override
            public void save(){
                //do not want save
            }
        };
        
        jenkins.putItem(project);
        final JobWithoutBuildsDiskUsageCalculation testCalculation = new JobWithoutBuildsDiskUsageCalculation();
        Thread t = new Thread(){
            public void run(){
                try {
                    testCalculation.execute(TaskListener.NULL);
                } catch (IOException ex) {
                    Logger.getLogger(JobDiskUsageCalculationThreadTest.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InterruptedException ex) {
                    Logger.getLogger(JobDiskUsageCalculationThreadTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        t.start();
        Thread.sleep(1000);
        testCalculation.execute(TaskListener.NULL);
        assertNull("Disk usage should not start calculation if preview calculation is in progress.", project.getProperty(DiskUsageProperty.class));
        t.interrupt();
    }
    
    public void testDoNotCalculateUnenabledDiskUsage() throws Exception{
        FreeStyleProject projectWithoutDiskUsage = jenkins.createProject(FreeStyleProject.class, "projectWithoutDiskUsage");
        DiskUsageProjectActionFactory.DESCRIPTOR.disableJobsDiskUsageCalculation();
        JobWithoutBuildsDiskUsageCalculation calculation = AperiodicWork.all().get(JobWithoutBuildsDiskUsageCalculation.class);
        calculation.execute(TaskListener.NULL);
        assertNull("Disk usage for build should not be counted.", projectWithoutDiskUsage.getProperty(DiskUsageProperty.class));
        DiskUsageProjectActionFactory.DESCRIPTOR.enableJobsDiskUsageCalculation();
    }
    
}
