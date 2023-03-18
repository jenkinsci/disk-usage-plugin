/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.PeriodicWork;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import jenkins.model.Jenkins;

/**
 *
 * @author jbrazdil
 */
@Extension
public class DiskUsageOvearallGraphGenerator extends PeriodicWork {

    @Override
    public long getRecurrencePeriod() {
        return PeriodicWork.DAY;
    }

    @Override
    protected void doRun() throws Exception {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        plugin.refreshGlobalInformation();
        File jobsDir = new File(Jenkins.get().getRootDir(), "jobs");
        Long freeJobsDirSpace = jobsDir.getTotalSpace();

        DiskUsageProjectActionFactory.DESCRIPTOR.addHistory(new DiskUsageOvearallGraphGenerator.DiskUsageRecord(plugin.getCashedGlobalBuildsDiskUsage(), plugin.getGlobalAgentDiskUsageWorkspace(), plugin.getCashedGlobalJobsWithoutBuildsDiskUsage(), freeJobsDirSpace, plugin.getCashedNonAgentDiskUsageWorkspace()));
        DiskUsageProjectActionFactory.DESCRIPTOR.save();
    }

    public static class DiskUsageRecord {
        private static final SimpleDateFormat sdf = new SimpleDateFormat("d/M");
        Date date;
        private Long jobsWithoutBuildsUsage = 0L;
        private Long allSpace = 0L;
        private Long diskUsageNonAgentWorkspaces = 0L;
        private Long buildUsage;
        private Long wsUsage;

        public DiskUsageRecord(Long diskUsageBuilds, Long diskUsageWorkspaces, Long diskUsageJobsWithoutBuilds, Long allSpace, Long diskUsageNonAgentWorkspaces) {
            this.buildUsage = diskUsageBuilds;
            this.wsUsage = diskUsageWorkspaces;
            this.jobsWithoutBuildsUsage = diskUsageJobsWithoutBuilds;
            this.allSpace = allSpace;
            this.diskUsageNonAgentWorkspaces = diskUsageNonAgentWorkspaces;
            date = new Date(){
                private static final long serialVersionUID = 1L;
                @Override
                public String toString() {
                    return sdf.format(this);
                }
            };
        }

        @Deprecated(forRemoval = true)
        public Long getNonSlaveWorkspacesUsage() {
            return getNonAgentWorkspacesUsage();
        }

        public Long getNonAgentWorkspacesUsage() {
            return Objects.requireNonNullElse(diskUsageNonAgentWorkspaces, 0L);
        }

        @Deprecated(forRemoval = true)
        public Long getSlaveWorkspacesUsage() {
            return getAgentWorkspacesUsage();
        }

        public Long getAgentWorkspacesUsage() {
            if(diskUsageNonAgentWorkspaces == null) {
                return getWorkspacesDiskUsage();
            }
            return getWorkspacesDiskUsage() - diskUsageNonAgentWorkspaces;
        }

        public Long getBuildsDiskUsage() {
            return Objects.requireNonNullElse(buildUsage, 0L);
        }

        public Long getJobsDiskUsage() {
            if(jobsWithoutBuildsUsage == null) {
                return getBuildsDiskUsage();
            }
            return jobsWithoutBuildsUsage + getBuildsDiskUsage();
        }

        public Long getAllSpace() {
            if(allSpace == null) {
                return 0L;
            }
            return allSpace;
        }

        public Long getWorkspacesDiskUsage() {
            if(wsUsage == null) {
                return 0L;
            }
            return wsUsage;
        }

        Date getDate() {
            return date;
        }
    }


}
