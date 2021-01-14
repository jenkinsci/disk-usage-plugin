/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.model.Item;
import java.util.Date;
import java.util.Map;

/**
 *
 * @author Lucie Votypkova
 */
public interface DiskUsageItemAction {

    public Map<String,Long> getBuildsDiskUsage(Date older, Date younger, boolean cashed);

    public Long getAllDiskUsageWorkspace(boolean cashed);

    public Long getAllCustomOrNonAgentWorkspaces(boolean cashed);

    public Long getAllDiskUsage(boolean cashed);

    public void actualizeCashedData();

    public void actualizeCashedBuildsData();

    public void actualizeCashedJobWithoutBuildsData();

    public void actualizeCashedWorkspaceData();

    public void actualizeCashedNotCustomWorkspaceData();

    public void actualizeAllCashedDate();

    public Long getAllDiskUsageWithoutBuilds(boolean cashed);

}
