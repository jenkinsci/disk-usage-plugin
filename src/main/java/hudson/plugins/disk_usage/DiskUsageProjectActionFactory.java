package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.*;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collection;
import java.util.Collections;

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
            showGraph = req.getParameter("disk_usage.showGraph") != null;
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
    }

}
