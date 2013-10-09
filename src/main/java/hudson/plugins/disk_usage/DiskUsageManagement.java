/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.model.RootAction;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageManagement extends ManagementLink implements RootAction{

   public final String[] COLUMNS = new String[]{"Project name", "Builds", "Workspace", "JobDirectory (without builds)"};

        public String getIconFileName() {
            return "/plugin/disk-usage/icons/diskusage48.png";
        }

        public String getDisplayName() {
            return Messages.DisplayName();
        }

        public String getUrlName() {
            return "plugin/disk-usage/";
        }

        @Override public String getDescription() {
            return Messages.Description();
        }     
    
}
