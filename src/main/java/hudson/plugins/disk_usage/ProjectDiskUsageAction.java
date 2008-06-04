package hudson.plugins.disk_usage;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.ChartUtil;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;
import java.awt.BasicStroke;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.Range;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Disk usage information holder for
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

    public BuildDiskUsageAction getLastBuildAction() {
        Run run = project.getLastBuild();
        if (run != null) {
            return run.getAction(BuildDiskUsageAction.class);
        }

        return null;
    }

    public Long getWorkspaceUsage() {
        if (project != null) {
            for (Run build : project.getBuilds()) {
                BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
                if (action != null) {
                    return action.wsUsage;
                }
            }
        }

        return null;
    }

    public Long getBuildUsage() {
        long buildUsage = 0;
        for (Run build : project.getBuilds()) {
            BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
            if (action != null) {
                buildUsage += action.buildUsage;
            }
        }

        return buildUsage;
    }

    public String getWorkspaceUsageString() {
        return getSizeString(getWorkspaceUsage());
    }

    public String getBuildUsageString() {
        return getSizeString(getBuildUsage());
    }

    /**
     * Generates a graph with disk usage trend
     * 
     */
    public void doGraph(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (ChartUtil.awtProblem) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
            return;
        }

        Run run = project.getLastBuild();
        if ((run == null) ||
                req.checkIfModified(run.getTimestamp(), rsp)) {
            return;
        }

        DataSetBuilder<String, NumberOnlyBuildLabel> dsb = new DataSetBuilder<String, NumberOnlyBuildLabel>();

        List<Object[]> usages = new ArrayList<Object[]>();
        long maxValue = 0;
        //First iteration just to get scale of the y-axis
        for (AbstractBuild build : project.getBuilds()) {
            BuildDiskUsageAction dua = build.getAction(BuildDiskUsageAction.class);
            if (dua != null) {
                maxValue = Math.max(maxValue, Math.max(dua.getWsUsage(), dua.getAllBuildsUsage()));
                usages.add(new Object[]{build, dua.getWsUsage(), dua.getAllBuildsUsage()});
            }
        }

        int floor = (int) getScale(maxValue);
        String unit = getUnitString(floor);
        double base = Math.pow(1024, floor);

        for (Object[] usage : usages) {
            NumberOnlyBuildLabel label = new NumberOnlyBuildLabel((AbstractBuild) usage[0]);
            dsb.add(((Long) usage[1]) / base, "workspace", label);
            dsb.add(((Long) usage[2]) / base, "all builds", label);
        }

        ChartUtil.generateGraph(req, rsp, createChart(req, dsb.build(), unit), 350, 150);
    }

    private JFreeChart createChart(StaplerRequest req, CategoryDataset dataset, String unit) {

        final JFreeChart chart = ChartFactory.createLineChart(
                null, // chart title
                null, // unused
                "disk usage (" + unit + ")", // range axis label
                dataset, // data
                PlotOrientation.VERTICAL, // orientation
                true, // include legend
                true, // tooltips
                false // urls
                );

        final LegendTitle legend = chart.getLegend();
        legend.setPosition(RectangleEdge.RIGHT);

        chart.setBackgroundPaint(Color.white);

        final CategoryPlot plot = chart.getCategoryPlot();

        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);

        CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setLowerBound(0);

        final LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
        renderer.setStroke(new BasicStroke(4.0f));
        ColorPalette.apply(renderer);

        plot.setInsets(new RectangleInsets(5.0, 0, 0, 5.0));

        return chart;
    }
}
