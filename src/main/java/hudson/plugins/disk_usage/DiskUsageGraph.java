/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.util.Graph;
import hudson.util.ShiftedCategoryAxis;
import java.awt.Color;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleEdge;

/**
 *
 * @author jbrazdil
 */
public class DiskUsageGraph extends Graph{
	CategoryDataset dataset;
        CategoryDataset workspaceDataset;
	String unit;

        public DiskUsageGraph(CategoryDataset dataset, String unit, CategoryDataset workspaceDataset){
		super(-1,350,150);
                this.workspaceDataset = workspaceDataset;
		this.dataset = dataset;
		this.unit = unit;
	}

	@Override
	protected JFreeChart createGraph() {
         
            
		final JFreeChart chart = ChartFactory.createAreaChart(
				null, // chart title
				null, // unused
				Messages.ProjectDiskUsage() + " (" + unit + ")", // range axis label
				dataset, // data
				PlotOrientation.VERTICAL, // orientation
				true, // include legend
				true, // tooltips
				false // urls
				);

		final LegendTitle legend = chart.getLegend();
		legend.setPosition(RectangleEdge.RIGHT);

		chart.setBackgroundPaint(Color.white);
		CategoryPlot plot = (CategoryPlot) chart.getPlot();

		plot.setBackgroundPaint(Color.WHITE);
		plot.setOutlinePaint(null);
		plot.setRangeGridlinesVisible(true);
		plot.setRangeGridlinePaint(Color.black);
		CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
		plot.setDomainAxis(domainAxis);
		domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
		domainAxis.setLowerMargin(0.0);
		domainAxis.setUpperMargin(0.0);
		// voodoo for better spacing between labels with many columns
		domainAxis.setCategoryMargin(-((double) dataset.getColumnCount() / 10.0));
                plot.setRangeAxis(1, plot.getRangeAxis(0));
                plot.setDataset(1, workspaceDataset);
                LineAndShapeRenderer renderer = new LineAndShapeRenderer();
                plot.setRenderer(1, renderer);
                setColorForArea(plot.getRenderer(), dataset.getRowCount()>2);
                plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
                
		return chart;
	}
        
        public void setColorForArea(CategoryItemRenderer renderer, boolean global){
            if(global){
                renderer.setSeriesPaint(0, Color.LIGHT_GRAY);
                renderer.setSeriesPaint(1, new Color(60,179,113));
                renderer.setSeriesPaint(2, new Color(106,90,205));
            }
            else{
                renderer.setSeriesPaint(0, new Color(60,179,113));
                renderer.setSeriesPaint(1, new Color(106,90,205));
            }
        }
        

}
