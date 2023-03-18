/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.project;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.plugins.disk_usage.DiskUsageUtil;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author lucinka
 */
public class DiskUsagePostBuildCalculation extends Recorder {

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
        throws InterruptedException, IOException
    {
        listener.getLogger().println("append disk usage");
        DiskUsageUtil.calculationDiskUsageOfBuild(build, listener);
        return true;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }


    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Calcualete disk usage of build";
        }

        @Override
        public DiskUsagePostBuildCalculation newInstance(StaplerRequest req, @NonNull JSONObject formData) throws FormException {
            return new DiskUsagePostBuildCalculation();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

}
