/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import edu.umd.cs.findbugs.annotations.NonNull;
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

    @Override
    public String getIconFileName() {
        return "symbol-disk-usage plugin-disk-usage";
    }

    @Override
    public String getDisplayName() {
        return Messages.displayName();
    }

    @Override
    public String getUrlName() {
        return "disk-usage";
    }

    @Override
    public String getDescription() {
        return Messages.description();
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.STATUS;
    }

    public void doIndex(StaplerRequest req, StaplerResponse res) throws ServletException, IOException {
        res.sendRedirect(Jenkins.get().getRootUrlFromRequest() + "plugin/disk-usage");
    }
}
