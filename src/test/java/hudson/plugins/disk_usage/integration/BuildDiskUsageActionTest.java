package hudson.plugins.disk_usage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.plugins.disk_usage.BuildDiskUsageAction;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
public class BuildDiskUsageActionTest {

    @Test
    void testGetAllDiskUsage(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        MatrixProject matrixProject = j.jenkins.createProject(MatrixProject.class, "project2");
        TextAxis axis1 = new TextAxis("axis", "axisA", "axisB", "axisC");
        TextAxis axis2 = new TextAxis("axis2", "Aaxis", "Baxis", "Caxis");
        AxisList list = new AxisList();
        list.add(axis1);
        list.add(axis2);
        matrixProject.setAxes(list);
        j.buildAndAssertSuccess(project);
        AbstractBuild<?,?> build = project.getLastBuild();
        j.buildAndAssertSuccess(matrixProject);
        MatrixBuild matrixBuild1 = matrixProject.getLastBuild();
        j.buildAndAssertSuccess(matrixProject);
        MatrixBuild matrixBuild2 = matrixProject.getLastBuild();
        Long sizeofBuild = 7546L;
        Long sizeOfMatrixBuild1 = 6800L;
        Long sizeOfMatrixBuild2 = 14032L;
        DiskUsageTestUtil.getBuildDiskUsageAction(build).setDiskUsage(sizeofBuild);
        DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild1).setDiskUsage(sizeOfMatrixBuild1);
        DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild2).setDiskUsage(sizeOfMatrixBuild2);
        long size1 = 5390;
        long size2 = 2390;
        int count = 1;
        Long matrixBuild1TotalSize = sizeOfMatrixBuild1;
        Long matrixBuild2TotalSize = sizeOfMatrixBuild2;
        for(MatrixConfiguration c: matrixProject.getItems()) {
            AbstractBuild<?,?> configurationBuild = c.getBuildByNumber(1);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild).setDiskUsage(count * size1);
            matrixBuild1TotalSize += count * size1;
            AbstractBuild<?,?> configurationBuild2 = c.getBuildByNumber(2);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild2).setDiskUsage(count * size2);
            matrixBuild2TotalSize += count * size2;
            count++;
        }
        assertEquals(sizeofBuild, DiskUsageTestUtil.getBuildDiskUsageAction(build).getAllDiskUsage(), "BuildDiskUsageAction for build 1 of FreeStyleProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.");
        assertEquals(matrixBuild1TotalSize, DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild1).getAllDiskUsage(), "BuildDiskUsageAction for build 1 of MatrixProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.");
        assertEquals(matrixBuild2TotalSize, DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild2).getAllDiskUsage(), "BuildDiskUsageAction for build 2 of MatrixProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.");

    }

    @Test
    void getBuildUsageStringMatrixProject(JenkinsRule j) throws Exception {
        MatrixProject matrixProject = j.jenkins.createProject(MatrixProject.class, "project2");
        TextAxis axis1 = new TextAxis("axis", "axisA", "axisB", "axisC");
        TextAxis axis2 = new TextAxis("axis2", "Aaxis", "Baxis", "Caxis");
        AxisList list = new AxisList();
        list.add(axis1);
        list.add(axis2);
        matrixProject.setAxes(list);
        j.buildAndAssertSuccess(matrixProject);
        MatrixBuild matrixBuild = matrixProject.getLastBuild();
        matrixProject.setAxes(list);
        Long kiloBytes = 2048L;
        int count = 0;
        for(MatrixConfiguration c: matrixProject.getItems()) {
            AbstractBuild<?,?> configurationBuild = c.getBuildByNumber(1);
            for(Action action: configurationBuild.getAllActions()) {
                if(action instanceof BuildDiskUsageAction) {
                    BuildDiskUsageAction a = (BuildDiskUsageAction) action;
                    a.setDiskUsage(kiloBytes);
                }
            }
            count++;
        }
        BuildDiskUsageAction action = null;
        for(Action a: matrixBuild.getAllActions()) {
            if(a instanceof BuildDiskUsageAction) {
                action = (BuildDiskUsageAction) a ;
                action.setDiskUsage(kiloBytes);
                break;
            }
        }
        count++;
        String size = (kiloBytes * count / 1024) + " KB";
        assertEquals(size, action.getBuildUsageString(), "String representation of build disk usage which has "  + size + " is wrong.");
    }

    @Test
    void getBuildUsageStringFreeStyleProject(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        j.buildAndAssertSuccess(project);
        AbstractBuild<?,?> build = project.getLastBuild();
        Long bytes = 100L;
        long kiloBytes = 2048L;
        long megaBytes = kiloBytes * 1024;
        long gygaBytes = megaBytes * 1024;
        Long teraBytes = gygaBytes * 1024;
        DiskUsageTestUtil.getBuildDiskUsageAction(build).setDiskUsage(bytes);
        assertEquals("100 B", DiskUsageTestUtil.getBuildDiskUsageAction(build).getBuildUsageString(), "String representation of build disk usage is wrong which has 100 B is wrong.");
        DiskUsageTestUtil.getBuildDiskUsageAction(build).setDiskUsage(kiloBytes);
        assertEquals("2 KB", DiskUsageTestUtil.getBuildDiskUsageAction(build).getBuildUsageString(), "String representation of build disk usage is wrong which has 2 KB is wrong.");
        DiskUsageTestUtil.getBuildDiskUsageAction(build).setDiskUsage(megaBytes);
        assertEquals("2 MB", DiskUsageTestUtil.getBuildDiskUsageAction(build).getBuildUsageString(), "String representation of build disk usage is wrong which has 2 MB is wrong.");
        DiskUsageTestUtil.getBuildDiskUsageAction(build).setDiskUsage(gygaBytes);
        assertEquals("2 GB", DiskUsageTestUtil.getBuildDiskUsageAction(build).getBuildUsageString(), "String representation of build disk usage is wrong which has 2 GB is wrong.");
        DiskUsageTestUtil.getBuildDiskUsageAction(build).setDiskUsage(teraBytes);
        assertEquals("2 TB", DiskUsageTestUtil.getBuildDiskUsageAction(build).getBuildUsageString(), "String representation of build disk usage is wrong which has 2T B is wrong.");
    }

}
