package hudson.plugins.disk_usage;

import org.junit.Test;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.AxisList;
import hudson.matrix.TextAxis;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsagePropertyTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void testGetAllDiskUsageWithoutBuilds() throws Exception{
        FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, "project1");
        MatrixProject matrixProject = j.jenkins.createProject(MatrixProject.class, "project2");
        TextAxis axis1 = new TextAxis("axis", "axisA", "axisB", "axisC");
        TextAxis axis2 = new TextAxis("axis2", "Aaxis", "Baxis", "Caxis");
        AxisList list = new AxisList();
        list.add(axis1);
        list.add(axis2);
        matrixProject.setAxes(list);       
        Long sizeOfProject = 7546l;
        Long sizeOfMatrixProject = 6800l;
        DiskUsageProperty projectProperty = new DiskUsageProperty();
        projectProperty.setDiskUsageWithoutBuilds(sizeOfProject);
        project.addProperty(projectProperty);
        DiskUsageProperty matrixProjectProperty = new DiskUsageProperty();
        matrixProjectProperty.setDiskUsageWithoutBuilds(sizeOfMatrixProject);
        matrixProject.addProperty(matrixProjectProperty);
        long size1 = 5390;
        int count = 1;
        Long matrixProjectTotalSize = sizeOfMatrixProject;
        for(MatrixConfiguration c: matrixProject.getItems()){
            DiskUsageProperty configurationProperty = new DiskUsageProperty();
            configurationProperty.setDiskUsageWithoutBuilds(count*size1);
            c.addProperty(configurationProperty);  
            matrixProjectTotalSize += count*size1;
            count++;
        }
        assertEquals("DiskUsageProperty for FreeStyleProject " + project.getDisplayName() + " returns wrong value its size without builds and including sub-projects.", sizeOfProject, project.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds());
        assertEquals("DiskUsageProperty for MatrixProject " + project.getDisplayName() + " returns wrong value for its size without builds and including sub-projects.", matrixProjectTotalSize, matrixProject.getProperty(DiskUsageProperty.class).getAllDiskUsageWithoutBuilds());
    }
}
