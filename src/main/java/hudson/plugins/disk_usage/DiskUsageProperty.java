package hudson.plugins.disk_usage;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.*;
import hudson.Extension;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.Collections;

//(basically nothing to see here)
/**
 * This Property sets DiskUsage action. 
 * 
 * @author dvrzalik
 */
@Deprecated
public class DiskUsageProperty extends JobProperty<Job<?, ?>> {

    @Override
    public Collection<? extends Action> getJobActions(Job<?, ?> job) {
        return Collections.emptyList();
    }

    /**
     * convert legacy DiskUsageProperty configuration to DiskUsageProjectActionFactory
     * @throws IOException
     */
    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void transitionAuth() throws IOException {
        DiskUsageDescriptor that = (DiskUsageDescriptor) Hudson.getInstance().getDescriptor(DiskUsageProperty.class);
        if (!that.converted) {
            DiskUsageProjectActionFactory.DESCRIPTOR.setShowGraph(that.showGraph);
            that.converted = true;
            that.save();
            DiskUsageProjectActionFactory.DESCRIPTOR.save();
        }
    }

    @Extension
    @Deprecated
    public static final class DiskUsageDescriptor extends JobPropertyDescriptor {

        public DiskUsageDescriptor() {
            load();
        }

        //Show graph on the project page?
        private boolean showGraph;

        private boolean converted;

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }


        @Override
        public DiskUsageProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
             return new DiskUsageProperty();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            showGraph = req.getParameter("disk_usage.showGraph") != null;
            save();
            return super.configure(req, formData);
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
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

    
