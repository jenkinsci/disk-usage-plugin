package hudson.plugins.disk_usage.integration;

import hudson.plugins.disk_usage.BuildDiskUsageAction;
import org.junit.Test;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.matrix.AxisList;
import hudson.matrix.TextAxis;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Rule;
import static org.junit.Assert.*;

/**
 *
 * @author Lucie Votypkova
 */
public class BuildDiskUsageActionTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void testGetAllDiskUsage() throws Exception{
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        MatrixProject matrixProject = j.jenkins.createProject(MatrixProject.class, "project2");
        TextAxis axis1 = new TextAxis("axis", "axisA", "axisB", "axisC");
        TextAxis axis2 = new TextAxis("axis2", "Aaxis", "Baxis", "Caxis");
        AxisList list = new AxisList();
        list.add(axis1);
        list.add(axis2);
        matrixProject.setAxes(list);
        j.buildAndAssertSuccess(project);
        AbstractBuild build = project.getLastBuild();
        j.buildAndAssertSuccess(matrixProject);
        MatrixBuild matrixBuild1 = matrixProject.getLastBuild();
        j.buildAndAssertSuccess(matrixProject);
        MatrixBuild matrixBuild2 = matrixProject.getLastBuild();
        Long sizeofBuild = 7546l;
        Long sizeOfMatrixBuild1 = 6800l;
        Long sizeOfMatrixBuild2 = 14032l;
        build.getAction(BuildDiskUsageAction.class).setDiskUsage(sizeofBuild);
        matrixBuild1.getAction(BuildDiskUsageAction.class).setDiskUsage(sizeOfMatrixBuild1);
        matrixBuild2.getAction(BuildDiskUsageAction.class).setDiskUsage(sizeOfMatrixBuild2);
        long size1 = 5390;
        long size2 = 2390;
        int count = 1;
        Long matrixBuild1TotalSize = sizeOfMatrixBuild1;
        Long matrixBuild2TotalSize = sizeOfMatrixBuild2;
        for(MatrixConfiguration c: matrixProject.getItems()){
            AbstractBuild configurationBuild = c.getBuildByNumber(1);
            configurationBuild.getAction(BuildDiskUsageAction.class).setDiskUsage(count*size1);
            matrixBuild1TotalSize += count*size1;
            AbstractBuild configurationBuild2 = c.getBuildByNumber(2);
            configurationBuild2.getAction(BuildDiskUsageAction.class).setDiskUsage(count*size2);
            matrixBuild2TotalSize += count*size2;
            count++;
        }
        assertEquals("BuildDiskUsageAction for build 1 of FreeStyleProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.", sizeofBuild, build.getAction(BuildDiskUsageAction.class).getAllDiskUsage());
        assertEquals("BuildDiskUsageAction for build 1 of MatrixProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.", matrixBuild1TotalSize, matrixBuild1.getAction(BuildDiskUsageAction.class).getAllDiskUsage());
        assertEquals("BuildDiskUsageAction for build 2 of MatrixProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.", matrixBuild2TotalSize, matrixBuild2.getAction(BuildDiskUsageAction.class).getAllDiskUsage());
        
    }
    
    @Test
    public void getBuildUsageStringMatrixProject() throws Exception{
        MatrixProject matrixProject = j.jenkins.createProject(MatrixProject.class, "project2");
        TextAxis axis1 = new TextAxis("axis", "axisA", "axisB", "axisC");
        TextAxis axis2 = new TextAxis("axis2", "Aaxis", "Baxis", "Caxis");
        AxisList list = new AxisList();
        list.add(axis1);
        list.add(axis2);
        matrixProject.setAxes(list);
        j.buildAndAssertSuccess(matrixProject);
        MatrixBuild matrixBuild = matrixProject.getLastBuild();
        matrixProject.setAxes(list);;
        Long kiloBytes = 2048l;
        int count = 0;
        for(MatrixConfiguration c: matrixProject.getItems()){
            AbstractBuild configurationBuild = c.getBuildByNumber(1);
             configurationBuild.getAction(BuildDiskUsageAction.class).setDiskUsage(kiloBytes);
            count++;
        }
        matrixBuild.getAction(BuildDiskUsageAction.class).setDiskUsage(kiloBytes);
        count ++;
        String size = (kiloBytes*count/1024) + " KB";
        assertEquals("String representation of build disk usage which has "  + size + " is wrong.", size, matrixBuild.getAction(BuildDiskUsageAction.class).getBuildUsageString());
        }
    
    @Test
    public void getBuildUsageStringFreeStyleProject() throws Exception{
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        j.buildAndAssertSuccess(project);
        AbstractBuild build = project.getLastBuild();
        Long bytes = 100l;
        Long kiloBytes = 2048l;
        Long megaBytes = kiloBytes*1024;
        Long gygaBytes = megaBytes*1024;
        Long teraBytes = gygaBytes*1024;
        build.getAction(BuildDiskUsageAction.class).setDiskUsage(bytes);
        assertEquals("String representation of build disk usage is wrong which has 100 B is wrong.", "100 B", build.getAction(BuildDiskUsageAction.class).getBuildUsageString());
        build.getAction(BuildDiskUsageAction.class).setDiskUsage(kiloBytes);
        assertEquals("String representation of build disk usage is wrong which has 2 KB is wrong.", "2 KB", build.getAction(BuildDiskUsageAction.class).getBuildUsageString());
        build.getAction(BuildDiskUsageAction.class).setDiskUsage(megaBytes);
        assertEquals("String representation of build disk usage is wrong which has 2 MB is wrong.", "2 MB", build.getAction(BuildDiskUsageAction.class).getBuildUsageString());
        build.getAction(BuildDiskUsageAction.class).setDiskUsage(gygaBytes);
        assertEquals("String representation of build disk usage is wrong which has 2 GB is wrong.", "2 GB", build.getAction(BuildDiskUsageAction.class).getBuildUsageString());
        build.getAction(BuildDiskUsageAction.class).setDiskUsage(teraBytes);
        assertEquals("String representation of build disk usage is wrong which has 2T B is wrong.", "2 TB", build.getAction(BuildDiskUsageAction.class).getBuildUsageString());
    }
 
}
