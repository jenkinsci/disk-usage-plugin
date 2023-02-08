/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.model.RootAction;
import java.io.IOException;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageManagement extends ManagementLink implements RootAction {

    public final String[] COLUMNS = new String[]{"Project name", "Builds", "Workspace", "JobDirectory (without builds)"};

    public String getIconFileName() {
        return "/plugin/disk-usage/icons/disk-usage.svg";
    }

    public String getDisplayName() {
        return Messages.displayName();
    }

    public String getUrlName() {
        return "disk-usage";
    }

    @Override
    public String getDescription() {
        return Messages.description();
    }

    public void doIndex(StaplerRequest req, StaplerResponse res) throws ServletException, IOException {
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        res.sendRedirect(Jenkins.getInstance().getRootUrlFromRequest() + "plugin/disk-usage");
    }

}
