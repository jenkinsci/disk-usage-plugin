package hudson.plugins.disk_usage;

import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import java.util.ArrayList;
import java.util.List;
import hudson.model.AbstractBuild;
import java.io.File;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import org.junit.Test;
/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageUtilTest extends HudsonTestCase{
    
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
        Long lenght = 0l;
        for(File file: files){
            lenght += file.length();
        }
        return lenght;
    }
    
    @Test
    @LocalData
    public void testCalculateDiskUsageForBuild() throws Exception{
        FreeStyleProject project = (FreeStyleProject) jenkins.getItem("project1");
        AbstractBuild build = project.getBuildByNumber(2);
        File file = new File(build.getRootDir(), "fileList");
        Long size = getSize(readFileList(file)) + build.getRootDir().length();
        DiskUsageUtil.calculateDiskUsageForBuild(build);
        Assert.assertEquals("Calculation of build disk usage does not return right size of build directory.", size, build.getAction(BuildDiskUsageAction.class).diskUsage);
    }
    
    @Test
    @LocalData
    public void testCalculateDiskUsageForMatrixBuild() throws Exception{
        MatrixProject project = (MatrixProject) jenkins.getItem("project1");
        AbstractBuild build = project.getBuildByNumber(1);
        File file = new File(build.getRootDir(), "fileList");
        Long size = getSize(readFileList(file)) + build.getRootDir().length();
        Long sizeAll = size;
        for(MatrixConfiguration config: project.getActiveConfigurations()){
            AbstractBuild b = config.getBuildByNumber(1);
            File f = new File(b.getRootDir(), "fileList");
            sizeAll += getSize(readFileList(f)) + build.getRootDir().length();
        }
        DiskUsageUtil.calculateDiskUsageForBuild(build);
        Assert.assertEquals("Matrix project project1 has disk usage size.", size, build.getAction(BuildDiskUsageAction.class).diskUsage);
        for(MatrixConfiguration config: project.getActiveConfigurations()){
            DiskUsageUtil.calculateDiskUsageForBuild(config.getBuildByNumber(1));
        }
        Assert.assertEquals("Matrix project project1 has wrong size for its build.", sizeAll, build.getAction(BuildDiskUsageAction.class).getAllDiskUsage());
    }
    
    @Test
    @LocalData
    public void testCalculateDiskUsageForJob() throws Exception{
        FreeStyleProject project = (FreeStyleProject) jenkins.getItem("project1");
        File file = new File(project.getRootDir(), "fileList");
        Long size = getSize(readFileList(file)) + project.getRootDir().length();
        DiskUsageUtil.calculateDiskUsageForProject(project);
        Assert.assertEquals("Calculation of job disk usage does not return right size of job without builds.", size, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds());
        
    }
    
    @Test
    @LocalData
    public void testCalculateDiskUsageForMatrixJob() throws Exception{
        MatrixProject project = (MatrixProject) jenkins.getItem("project1");
        File file = new File(project.getRootDir(), "fileList");
        Long size = getSize(readFileList(file)) + project.getRootDir().length();
        Long sizeAll = size;
        for(MatrixConfiguration config: project.getItems()){
            File f = new File(config.getRootDir(), "fileList");
            sizeAll += getSize(readFileList(f)) + config.getRootDir().length();
        }
        DiskUsageUtil.calculateDiskUsageForProject(project);
        Assert.assertEquals("Calculation of job disk usage does not return right size of job without builds.", size, project.getAction(ProjectDiskUsageAction.class).getDiskUsageWithoutBuilds());
        for(AbstractProject p: project.getItems()){
            DiskUsageUtil.calculateDiskUsageForProject(p);
        }
        Assert.assertEquals("Calculation of job disk usage does not return right size of job and its sub-jobs without builds.", sizeAll, project.getAction(ProjectDiskUsageAction.class).getAllDiskUsageWithoutBuilds());
    
    }   
   
}
