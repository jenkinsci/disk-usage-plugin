package hudson.plugins.disk_usage.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Project;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.listeners.ItemListener;
import hudson.plugins.disk_usage.ProjectDiskUsageAction;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
public class ProjectDiskUsageActionTest {

    @Test
    void testGetBuildsDiskUsage(JenkinsRule j) throws Exception {
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
        long matrixBuild1TotalSize = sizeOfMatrixBuild1;
        long matrixBuild2TotalSize = sizeOfMatrixBuild2;
        for(MatrixConfiguration c: matrixProject.getItems()) {
            AbstractBuild<?,?> configurationBuild = c.getBuildByNumber(1);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild).setDiskUsage(count * size1);
            matrixBuild1TotalSize += count * size1;
            AbstractBuild<?,?> configurationBuild2 = c.getBuildByNumber(2);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild2).setDiskUsage(count * size2);
            matrixBuild2TotalSize += count * size2;
            count++;
        }
        Long matrixProjectBuildsTotalSize = matrixBuild1TotalSize + matrixBuild2TotalSize;
        assertEquals(sizeofBuild, project.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage().get("all"), "BuildDiskUsageAction for build 1 of FreeStyleProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.");
        assertEquals(matrixProjectBuildsTotalSize, matrixProject.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage().get("all"), "BuildDiskUsageAction for build 1 of MatrixProject " + matrixProject.getDisplayName() + " returns wrong value for its size including sub-builds.");

    }

    @Test
    void testGetBuildsDiskUsageNotDeletedConfigurations(JenkinsRule j) throws Exception {
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
        long sizeOfMatrixBuild2 = 14032L;
        DiskUsageTestUtil.getBuildDiskUsageAction(build).setDiskUsage(sizeofBuild);
        DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild1).setDiskUsage(sizeOfMatrixBuild1);
        DiskUsageTestUtil.getBuildDiskUsageAction(matrixBuild2).setDiskUsage(sizeOfMatrixBuild2);

        long size1 = 5390;
        long size2 = 2390;
        int count = 1;
        long matrixBuild1TotalSize = sizeOfMatrixBuild1;
        for(MatrixConfiguration c: matrixProject.getItems()) {

            final AbstractBuild<?,?> configurationBuild = c.getBuildByNumber(1);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild).setDiskUsage(count * size1);
            matrixBuild1TotalSize += count * size1;

            final AbstractBuild<?,?> configurationBuild2 = c.getBuildByNumber(2);
            DiskUsageTestUtil.getBuildDiskUsageAction(configurationBuild2).setDiskUsage(count * size2);

            count++;
        }

        matrixBuild2.delete();

        assertEquals(sizeofBuild, project.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage().get("all"), "BuildDiskUsageAction for build 1 of FreeStyleProject " + project.getDisplayName() + " returns wrong value for its size including sub-builds.");
        assertEquals(matrixBuild1TotalSize, matrixProject.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage().get("all").longValue(), "BuildDiskUsageAction for build 1 of MatrixProject " + matrixProject.getDisplayName() + " returns wrong value for its size including sub-builds.");

    }

    @Test
    void getAllBuildDiskUsageFiltered(JenkinsRule j) throws Exception {
        ProjectTest project = new ProjectTest(j.jenkins, "project");
        Calendar calendar1 = new GregorianCalendar();
        Calendar calendar2 = new GregorianCalendar();
        Calendar calendar3 = new GregorianCalendar();
        calendar1.set(2013, 9, 9);
        calendar2.set(2013, 8, 22);
        calendar3.set(2013, 5, 9);
        ProjectTestBuild build1 = project.createExecutable(calendar1);
        ProjectTestBuild build2 = project.createExecutable(calendar2);
        ProjectTestBuild build3 = project.createExecutable(calendar3);
        Calendar filterCalendar = new GregorianCalendar();
        filterCalendar.set(2013, 8, 30);
        Date youngerThan10days = filterCalendar.getTime();
        filterCalendar.set(2013, 9, 2);
        Date olderThan7days = filterCalendar.getTime();
        filterCalendar.set(2013, 8, 19);
        Date youngerThan3weeks = filterCalendar.getTime();
        filterCalendar.set(2013, 4, 9);
        Date olderThan5months = filterCalendar.getTime();
        filterCalendar.set(2013, 8, 19);
        Date olderThan3weeks = filterCalendar.getTime();
        long sizeofBuild1 = 7546L;
        Long sizeofBuild2 = 9546L;
        Long sizeofBuild3 = 15546L;
        DiskUsageTestUtil.getBuildDiskUsageAction(build1).setDiskUsage(sizeofBuild1);
        DiskUsageTestUtil.getBuildDiskUsageAction(build2).setDiskUsage(sizeofBuild2);
        DiskUsageTestUtil.getBuildDiskUsageAction(build3).setDiskUsage(sizeofBuild3);
        project.update();
        Map<String, Long> size = project.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage(null, youngerThan10days);
        assertEquals(sizeofBuild1, size.get("all"), 0, "Disk usage of builds should count only build 1 (only build 1 is younger than 10 days ago).");
        size = project.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage(olderThan7days, youngerThan10days);
        assertEquals(0, size.get("all"), 0, "Disk usage of builds should count only build 1 (only build 1 is younger than 10 days ago and older than 8 days ago).");
        size = project.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage(olderThan7days, null);
        assertEquals(sizeofBuild2 + sizeofBuild3, size.get("all"), 0, "Disk usage of builds should count all builds (all builds is older than 7 days ago).");
        size = project.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage(olderThan7days, youngerThan3weeks);
        assertEquals(sizeofBuild2, size.get("all"), 0, "Disk usage of builds should count build 1 and build 2 (build 1 and build 2 are older than 7 days but younger that 3 weeks).");
        size = project.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage(olderThan5months, null);
        assertEquals(0, size.get("all"), 0, "No builds is older than 5 months ago");
        size = project.getAction(ProjectDiskUsageAction.class).getBuildsDiskUsage(olderThan3weeks, null);
        assertEquals(sizeofBuild3, size.get("all"), 0, "Disk usage of builds should count only build 3 (only build 3 is older tah 3 weeks).");


    }

    @Test
    @LocalData
    void testNotToBreakLazyLoading(JenkinsRule j) throws IOException {
        AbstractProject<?,?> project = (AbstractProject<?,?>) j.jenkins.getItem("project1");
        project.isBuilding();
        int loadedBuilds = project._getRuns().getLoadedBuilds().size();
        assertTrue(8 > loadedBuilds, "This test does not have sense if there is loaded all builds");
        project.getAction(ProjectDiskUsageAction.class).getGraph();
        assertTrue(project._getRuns().getLoadedBuilds().size() <= loadedBuilds, "Creation of graph should not cause loading of builds.");

    }

    public class ProjectTest extends Project<ProjectTest, ProjectTestBuild> implements TopLevelItem {

        ProjectTest(ItemGroup group, String name) {
            super(group, name);
            onCreatedFromScratch();
            ItemListener.fireOnCreated(this);
        }

        @Override
        public Class<ProjectTestBuild> getBuildClass() {
            return ProjectTestBuild.class;
        }

        @Override
        public ProjectTestBuild createExecutable() throws IOException {
            ProjectTestBuild build = new ProjectTestBuild(this);
            builds.put(getNextBuildNumber(), build);
            return build;
        }

        public ProjectTestBuild createExecutable(Calendar calendar) throws IOException {
            ProjectTestBuild build = new ProjectTestBuild(this, calendar);
            build.number = this.assignBuildNumber();
            builds.put(build.number, build);
            return build;
        }

        @Override
        public TopLevelItemDescriptor getDescriptor() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void update() {
            this.updateTransientActions();
        }

        @Override
        public void save() {
            // do not save fake project
            getRootDir().mkdirs();
        }
    }

    public class ProjectTestBuild extends Build<ProjectTest, ProjectTestBuild> {


        public ProjectTestBuild(ProjectTest project) throws IOException {
            super(project);
        }

        public ProjectTestBuild(ProjectTest project, Calendar calendar) throws IOException {
            super(project, calendar);
        }

    }

}
