/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage.project;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.plugins.disk_usage.DiskUsageUtil;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author lucinka
 */
public class DiskUsagePostBuildCalculation extends Recorder{
    
    public DiskUsagePostBuildCalculation(){
    }
    
    @Override
      public boolean perform(Build<?, ?> build, Launcher launcher, BuildListener listener){
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
        public String getDisplayName() {
            return "Calcualete disk usage of build";
        }

        @Override
        public DiskUsagePostBuildCalculation newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new DiskUsagePostBuildCalculation();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
    
}
