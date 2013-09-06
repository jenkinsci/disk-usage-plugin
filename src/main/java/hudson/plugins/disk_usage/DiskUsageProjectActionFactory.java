package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.*;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class DiskUsageProjectActionFactory extends TransientProjectActionFactory
        implements Describable<DiskUsageProjectActionFactory> {

    @Override
    public Collection<? extends Action> createFor(AbstractProject job) {
        return Collections.singleton(new ProjectDiskUsageAction(job));
    }

    public Descriptor<DiskUsageProjectActionFactory> getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<DiskUsageProjectActionFactory> {

        public DescriptorImpl() {
            load();
        }

        //Show graph on the project page?
        private boolean showGraph;

		// Number of days in history
        private int historyLength = 183;

        // Timeout for a single Project's workspace analyze (in mn)
        private int timeoutWorkspace = 5;

		List<DiskUsageOvearallGraphGenerator.DiskUsageRecord> history = new LinkedList<DiskUsageOvearallGraphGenerator.DiskUsageRecord>(){
				private static final long serialVersionUID = 1L;

				@Override
				public boolean add(DiskUsageOvearallGraphGenerator.DiskUsageRecord e) {
					boolean ret = super.add(e);
					if(ret && this.size() > historyLength){
						this.removeRange(0, this.size() - historyLength);
					}
					return ret;
				}
			};

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }


        @Override
        public DiskUsageProjectActionFactory newInstance(StaplerRequest req, JSONObject formData) {
            return new DiskUsageProjectActionFactory();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // Configure showGraph
            showGraph = req.getParameter("disk_usage.showGraph") != null;

            // Configure historyLength
			String histlen = req.getParameter("disk_usage.historyLength");
			if(histlen != null ){
				try{
					historyLength = Integer.parseInt(histlen);
				}catch(NumberFormatException ex){
					historyLength = 183;
				}
			}else{
				historyLength = 183;
			}

            // Configure timeoutWorkspace
            String timeoutWks = req.getParameter("disk_usage.timeoutWorkspace");
            if (timeoutWks != null ) {
                try {
                    timeoutWorkspace = Integer.parseInt(timeoutWks);
                } catch (NumberFormatException ex) {
                    timeoutWorkspace = 5;
                }
            } else {
                timeoutWorkspace = 5;
            }
            save();
            return super.configure(req, formData);
        }

        public boolean isShowGraph() {
            //The graph is shown by default
            return showGraph;
        }

        public void setShowGraph(Boolean showGraph) {
            this.showGraph = showGraph;
        }

        public int getHistoryLength() {
            return historyLength;
        }

        public void setHistoryLength(Integer historyLength) {
            this.historyLength = historyLength;
        }

        public int getTimeoutWorkspace() {
            return timeoutWorkspace;
        }

        public void setTimeoutWorkspace(Integer timeoutWorkspace) {
            this.timeoutWorkspace = timeoutWorkspace;
        }
    }

}
