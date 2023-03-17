package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildBadgeAction;
import hudson.model.ItemGroup;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import java.io.IOException;
import java.util.Set;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Disk usage information for a single build
 * @author dvrzalik
 */
// TODO really implementsProminentProjectAction???
@ExportedBean(defaultVisibility = 1)
public class BuildDiskUsageAction implements ProminentProjectAction, BuildBadgeAction, RunAction2 {

    AbstractBuild<?,?> build;

    public BuildDiskUsageAction(AbstractBuild<?,?> build) {
        this.build = build;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.displayName();
    }

    @Override
    public String getUrlName() {
        return Messages.urlName();
    }

    public void setDiskUsage(Long size) throws IOException {
        AbstractProject<?,?> project = build.getProject();
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
        }
        DiskUsageBuildInformation information = property.getDiskUsageBuildInformation(build.getId());
        if(information != null) {
            information.setSize(size);
        }
        else {
            property.getDiskUsage().addBuildInformation(new DiskUsageBuildInformation(build.getId(), build.getTimeInMillis(), build.getNumber(), size), build);
        }
        property.saveDiskUsage();
    }

    /**
     * @return Disk usage of the build (included child builds)
     */
    public Long getDiskUsage() {
        AbstractProject<?,?> project = build.getProject();
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
        }
        return property.getDiskUsageOfBuild(build.getId());
    }

    public Long getAllDiskUsage() {
        Long buildsDiskUsage = getDiskUsage();
        AbstractProject<?,?> project = build.getProject();
        if(project instanceof ItemGroup) {
            buildsDiskUsage += getBuildsDiskUsageAllSubItems((ItemGroup<?>) project);
        }
        return buildsDiskUsage;
    }

    public String getBuildUsageString() {
        return DiskUsageUtil.getSizeString(getAllDiskUsage());
    }

    private Long getBuildsDiskUsageAllSubItems(ItemGroup<?> group) {
        Long buildsDiskUsage = 0L;
        for(Object item: group.getItems()) {
            if(item instanceof ItemGroup) {
                buildsDiskUsage += getBuildsDiskUsageAllSubItems((ItemGroup<?>) item);
            }
            else {
                if(item instanceof AbstractProject) {
                    AbstractProject<?,?> project = (AbstractProject<?,?>) item;
                    DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
                    if(property == null) {
                        DiskUsageUtil.addProperty(project);
                        property = project.getProperty(DiskUsageProperty.class);
                    }
                    Set<DiskUsageBuildInformation> informations = property.getDiskUsageOfBuilds();
                    for(DiskUsageBuildInformation information:  informations) {
                        if(information.getNumber() == build.getNumber()) {
                            buildsDiskUsage += information.getSize();
                        }
                    }
                }
            }
        }
        return buildsDiskUsage;
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        // no action is needed
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        DiskUsageProperty property = build.getProject().getProperty(DiskUsageProperty.class);
        long size = 0L;
        if(property == null) {
            return;
        }

        // Transient actions can be created even during deletion of job
        if(property.getDiskUsageBuildInformation(build.getNumber()) == null && build.getRootDir().exists()) {
            property.getDiskUsage().addBuildInformation(new DiskUsageBuildInformation(build.getId(), build.getTimeInMillis(), build.getNumber(), size), build);
        }
    }

}
