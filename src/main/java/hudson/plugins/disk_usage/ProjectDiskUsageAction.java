package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.Hudson;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Disk usage of a project
 * 
 * @author dvrzalik
 */
public class ProjectDiskUsageAction extends DiskUsageAction {

    AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project;

    public ProjectDiskUsageAction(AbstractProject<? extends AbstractProject, ? extends AbstractBuild> project) {
        this.project = project;
    }

    @Override
    public String getUrlName() {
        return "disk-usage";
    }
    
    /**
     * @return Disk usage for all builds
     */
    public DiskUsage getDiskUsage() {
        DiskUsage du = new DiskUsage(0, 0);

        if (project != null) {
            BuildDiskUsageAction action = null;
            Iterator<? extends AbstractBuild> buildIterator = project.getBuilds().iterator();
            while ((action == null) && buildIterator.hasNext()) {
                action = buildIterator.next().getAction(BuildDiskUsageAction.class);
            }
            if (action != null) {
                DiskUsage bdu = action.getDiskUsage();
                //Take last available workspace size
                du.wsUsage = bdu.getWsUsage();
                du.buildUsage += bdu.getBuildUsage();
            }

            while (buildIterator.hasNext()) {
                action = buildIterator.next().getAction(BuildDiskUsageAction.class);
                if (action != null) {
                    du.buildUsage += action.getDiskUsage().getBuildUsage();
                }
            }
            
        }

        return du;
    }
    
    public BuildDiskUsageAction getLastBuildAction() {
        Run run = project.getLastBuild();
        if (run != null) {
            return run.getAction(BuildDiskUsageAction.class);
        }

        return null;
    }

    /**
     * Generates a graph with disk usage trend
     * 
     */
    public Graph getGraph() throws IOException {
        //TODO if(nothing_changed) return;

        DataSetBuilder<String, NumberOnlyBuildLabel> dsb = new DataSetBuilder<String, NumberOnlyBuildLabel>();

        List<Object[]> usages = new ArrayList<Object[]>();
        long maxValue = 0;
        //First iteration just to get scale of the y-axis
        for (AbstractBuild build : project.getBuilds()) {
            BuildDiskUsageAction dua = build.getAction(BuildDiskUsageAction.class);
            if (dua != null) {
                DiskUsage usage = dua.getDiskUsage();
                maxValue = Math.max(maxValue, Math.max(usage.wsUsage, usage.getBuildUsage()));
                usages.add(new Object[]{build, usage.wsUsage, usage.getBuildUsage()});
            }
        }

        int floor = (int) DiskUsage.getScale(maxValue);
        String unit = DiskUsage.getUnitString(floor);
        double base = Math.pow(1024, floor);

        for (Object[] usage : usages) {
            NumberOnlyBuildLabel label = new NumberOnlyBuildLabel((AbstractBuild) usage[0]);
            dsb.add(((Long) usage[1]) / base, "workspace", label);
            dsb.add(((Long) usage[2]) / base, "build", label);
        }

		return new DiskUsageGraph(dsb.build(), unit);
    }

    /** Shortcut for the jelly view */
    public boolean showGraph() {
        return Hudson.getInstance().getDescriptorByType(DiskUsageProjectActionFactory.DescriptorImpl.class).isShowGraph();
    }
}
