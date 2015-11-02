/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.plugins.disk_usage.unused.DiskUsageItemGroup;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import jenkins.model.Jenkins;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageItemGroupAction implements Action{
    
    private ItemGroup group;
    
    public DiskUsageItemGroupAction(ItemGroup group){
        this.group = group;
    }
    
    public Collection getItems(){
        return group.getItems();
    }
    
    public DiskUsageItemGroup getDiskUsageItemGroup(){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        DiskUsageItemGroup usage = plugin.getDiskUsageItemGroup(group);
        return usage;
    }
    
    public Set<String> getUnloadedJobs(){
       DiskUsageItemGroup usage = getDiskUsageItemGroup();
       return usage.getDiskUsageOfNotLoadedJobs().keySet();
    }
    
    public String getDiskUsageOfUnloadedJobeInString(String path){
        return DiskUsageUtil.getSizeString(getDiskUsageOfUnloadedJob(path));
    }
    
    public Long getDiskUsageOfUnloadedJob(String path){
        DiskUsageItemGroup usage = getDiskUsageItemGroup();
        return usage.getDiskUsageOfNotLoadedJob(path);
    }
    
    public Long getDiskUsageOfUnloadedJobs(){
        Long size = 0L;
        for(Long value : getDiskUsageItemGroup().getDiskUsageOfNotLoadedJobs().values()){
            size += value;
        }
        return size;
    }
    
    public String getDiskUsageOfUnloadedJobsInString(){
        return DiskUsageUtil.getSizeString(getDiskUsageOfUnloadedJobs());
    }
    
    
    
    public Long getDiskUsage(){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        DiskUsageItemGroup usage = plugin.getDiskUsageItemGroup(group);
        return usage.getDiskUsage();
    }
    
    public Long getAllDiskUsage() throws IOException{
        Long diskUsage = getDiskUsage();
        for(Item item : (Collection<Item>) group.getItems()){
            if(item instanceof AbstractProject){
                AbstractProject project = (AbstractProject) item;
                ProjectDiskUsageAction action = project.getAction(ProjectDiskUsageAction.class);
                diskUsage += action.getAllBuildsDiskUsage();
                diskUsage += action.getAllDiskUsageWithoutBuilds();
            }
            else{
                if(item instanceof ItemGroup){
                    DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
                    DiskUsageItemGroup usage = plugin.getDiskUsageItemGroup(group);
                    diskUsage += usage.getAllDiskUsage();
                }
            }
        }
        return diskUsage;
    }


    @Override
    public String getIconFileName() {
        return "/plugin/disk-usage/icons/diskusage48.png";
    }

    @Override
    public String getDisplayName() {
        return "Disk usage overview";
    }

    @Override
    public String getUrlName() {
        return "disk-usage";
    }
    
}
