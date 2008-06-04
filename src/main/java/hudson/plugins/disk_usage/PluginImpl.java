package hudson.plugins.disk_usage;

import hudson.Plugin;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Jobs;
import hudson.model.ManagementLink;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the the plugin.
 *
 * @author dvrzalik
 * @plugin
 */
public class PluginImpl extends Plugin {

    public static final String[] COLUMNS = new String[] {"Project name","Builds","Workspace"};
    
    public void start() throws Exception {
        
        Jobs.PROPERTIES.add(DiskUsageProperty.DESCRIPTOR);

        ManagementLink.LIST.add(new ManagementLink() {

            public final String[] COLUMNS = new String[]{"Project name", "Builds", "Workspace"};

            public String getIconFileName() {
                return "/plugin/disk-usage/icons/diskusage48.gif";
            }

            public String getDisplayName() {
                return "Disk usage";
            }

            public String getUrlName() {
                return "plugin/disk-usage/";
            }

            public String getDescription() {
                return "Display per-project disk usage";
            }
        });
    }

    /**
     * @return List of Lists of Strings
     */
    public List<List<String>> getUsages() {
        ArrayList<List<String>> result = new ArrayList<List<String>>();

        List<AbstractProject> projects = Util.createSubList(Hudson.getInstance().getItems(), AbstractProject.class);

        for (AbstractProject project : projects) {
            List row = new ArrayList<String>(3);
            row.add("<b><a href=" + Util.encode(project.getAbsoluteUrl()) + ">" + project.getName() + "</a></b>");
            ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
            if (action != null) {
                row.add(action.getBuildUsageString());
                row.add(action.getWorkspaceUsageString());
            } else {
                row.add("-");
                row.add("-");
            }
            result.add(row);
        }

        return result;
    }
}
