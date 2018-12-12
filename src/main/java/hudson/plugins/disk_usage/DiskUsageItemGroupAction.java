/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import com.google.common.collect.Lists;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.plugins.disk_usage.unused.DiskUsageItemGroup;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Lucie Votypkova
 */
public class DiskUsageItemGroupAction implements Action, DiskUsageItemAction{
    
    private ItemGroup group;
    
    private DiskUsageItemGroup diskUsage;
    
    public DiskUsageItemGroupAction(DiskUsageItemGroup diskUsage){
        this.group = diskUsage.getItemGroup();
        this.diskUsage = diskUsage;
    }
    
    public Collection<Item> getItems(){
        return group.getItems();
    }
    
    public DiskUsageItemGroup getDiskUsageItemGroup(){
        return diskUsage;
    }
    
    public Collection<String> getUnloadedJobs(){
       DiskUsageItemGroup usage = getDiskUsageItemGroup();
       return usage.getNotLoadedJobs().keySet();
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
        for(Long value : getDiskUsageItemGroup().getNotLoadedJobs().values()){
            size += value;
        }
        return size;
    }
    
    public String getDiskUsageOfUnloadedJobsInString(){
        return DiskUsageUtil.getSizeString(getDiskUsageOfUnloadedJobs());
    }
    
    public Long getAllDiskUsage(Item item, boolean cached){
        DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
        if(action != null){
            return action.getAllDiskUsage(cached);
        }
        return 0L;
    }
    
    
    
    @Override
    public Long getAllDiskUsage(boolean cached) {
        Long size = getAllDiskUsageNotLoadedJobs(cached) + this.getAllDiskUsageWithoutBuilds(cached) + this.getBuildsDiskUsage(null, null, cached).get("all");
        //loaded
        for(Item item : getItems()){
            DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
            if(action!=null){

               size += action.getAllDiskUsage(cached);
            }
        }
        return size;
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
    
    public String getSizeInString(Long size){
        return DiskUsageUtil.getSizeString(size);
    }

    public String getAllDiskUsageInString(boolean cached) {
        return DiskUsageUtil.getSizeString(getAllDiskUsage(cached));
    }


    public Collection<Item> getSubItems() {
        return group.getItems();
    }
    
    public Long getDiskUsageLockedBuilds(Item item) throws IOException{
        return getDiskUsageBuilds(item, "locked");
    }

  
    public Long getDiskUsageLockedBuilds() throws IOException {
        return getDiskUsageBuilds("locked");  
    }
    
    private Long getDiskUsageBuilds(Item item, String type) throws IOException{
        DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
        if(action!=null){
            return DiskUsageUtil.getItemGroupAction((ItemGroup)item).getDiskUsageBuilds(type);
        }
        return 0L;
    }
    
    private Long getDiskUsageBuilds(String type) throws IOException{
        Long sizeLocked = 0L;
        for(Item item : getSubItems()){
            DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
            if(action!=null){
                sizeLocked += DiskUsageUtil.getItemGroupAction(group).getDiskUsageBuilds(type);
            }
        }
        return sizeLocked;
    }


    public Long getDiskUsageAllBuilds() throws IOException {
        return getDiskUsageBuilds("all");
    }
    
    public Long getDiskUsageAllBuilds(Item item) throws IOException{
        return getDiskUsageBuilds(item, "all");
    }
    
    public Long getDiskUsageNotLoadedBuilds() throws IOException{
        return getDiskUsageBuilds("notLoaded");
    }
    
    public Long getDiskUsageNotLoadedBuilds(Item item) throws IOException{
        return getDiskUsageBuilds(item, "notLoaded");
    }

    public String getDiskUsageLockedBuildsInString() throws IOException {
        return DiskUsageUtil.getSizeString(getDiskUsageNotLoadedBuilds());
    }


    public String getDiskUsageAllBuildsInString() throws IOException {
        return DiskUsageUtil.getSizeString(getDiskUsageAllBuilds());
    }
    
    public String getDiskUsageNotLoadedInString() throws IOException {
        return DiskUsageUtil.getSizeString(getDiskUsageNotLoadedBuilds());
    }
    
    
    public String getDiskUsageLockedBuildsInString(Item item) throws IOException {
        return DiskUsageUtil.getSizeString(getDiskUsageNotLoadedBuilds(item));
    }


    public String getDiskUsageAllBuildsInString(Item item) throws IOException {
        return DiskUsageUtil.getSizeString(getDiskUsageAllBuilds(item));
    }
    
    public String getDiskUsageNotLoadedInString(Item item) throws IOException {
        return DiskUsageUtil.getSizeString(getDiskUsageNotLoadedBuilds(item));
    }
    

    
    @Override
    public Map<String,Long> getBuildsDiskUsage(Date older, Date younger, boolean cached) {
        if(cached && older == null && younger ==null){
            //it is necessary to grab all information in case of filter, cached data are without filter
            return diskUsage.getCaschedDiskUsageBuilds();
        }
         Map<String,Long> usage = new HashMap<String,Long>();
         addAllBuildsDiskUsage(usage, older, younger, cached);
         diskUsage.setCaschedDiskUsageBuilds(usage);
         return usage;
    }


    
    public void addAllBuildsDiskUsage(Map<String,Long> usage, Date older, Date younger, boolean cached) {
        for(Item item : getItems() ){
            DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
            if(action!=null){
                Map<String,Long> projectUsage = action.getBuildsDiskUsage(older, younger, cached);
                for(String type: projectUsage.keySet()){
                    Long size = projectUsage.get(type);
                    Long sizeCounted = usage.get(type);
                    if(sizeCounted!=null){
                        size += sizeCounted;
                    }
                    usage.put(type, size);
                    continue;
                }
            }
        }
        
    }
    
    
    public Map<String, Long> getBuildsDiskUsage(Item item, Date older, Date younger, boolean cached) throws IOException {
        DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
        if(action!=null){
            return DiskUsageUtil.getDiskUsageItemAction(item).getBuildsDiskUsage(older, younger, cached);
        }
        return Collections.EMPTY_MAP;
    }
    
    @Override
    public Long getAllDiskUsageWorkspace(boolean cached){
        if(cached){
            return diskUsage.getCachedDiskUsageWorkspaces();
        }
        Long size = 0L;
        for(Item item : getItems()){
            DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
            if(action!=null){
                
               size += action.getAllDiskUsageWorkspace(cached);
            }
        }
        diskUsage.setCachedDiskUsageWorkspaces(size);
        return size;
        
    }
    
    public Long getDiskUsageWithoutBuilds(Item item, boolean cached){
        DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
        if(action!=null){
            return action.getAllDiskUsageWithoutBuilds(cached);
        }
        return 0L;
    }
    
    public Long getAllDiskUsageWorkspace(Item item, boolean cached){
        DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
        if(action!=null){
            return action.getAllDiskUsageWorkspace(cached);
        }
        return 0L;
    }
    
    public Long getAllCustomOrNonSlaveWorkspaces(Item item, boolean cached){
        DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
        if(action!=null){
            return action.getAllCustomOrNonSlaveWorkspaces(cached);
        }
        return 0L;
    }
    
     public void doFilter(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException{
        Date older = DiskUsageUtil.getDate(req.getParameter("older"), req.getParameter("olderUnit"));
        Date younger = DiskUsageUtil.getDate(req.getParameter("younger"), req.getParameter("youngerUnit"));
        req.setAttribute("filter", "filter");
        req.setAttribute("older", older);
        req.setAttribute("younger", younger);
        
        req.getView(this, "index.jelly").forward(req, rsp);     
    }
    
    @Override
    public Long getAllCustomOrNonSlaveWorkspaces(boolean cached){
        if(cached){
            return diskUsage.getCachedDiskUsageCustomWorkspaces();
        }
        Long size = 0L;
        for(Item item : getItems()){
            DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
            if(action!=null){
                size += action.getAllCustomOrNonSlaveWorkspaces(cached);
            }
        }
        diskUsage.setCachedDiskUsageCustomWorkspaces(size);
        return size;
    }
    
    public Long getAllDiskUsageNotLoadedJobs(boolean cached){
        if(cached){
            return diskUsage.getCachedDiskUsageNotLoadedJobs();
        }
        Long size = diskUsage.getDiskUsageOfNotLoadedJobs(cached);
        for(Item item : getItems()){
            if(item instanceof ItemGroup){
                DiskUsageItemGroupAction action = DiskUsageUtil.getItemGroupAction((ItemGroup)item);
                size += action.getAllDiskUsageNotLoadedJobs(cached);
            }
        }
        diskUsage.setCachedDiskUsageNotLoadedJobs(size);
        return size;
    }
    
    
    @Override
    public void actualizeCachedData() {
        actualizeCachedData(true);
    }


    private void actualizeCachedData(boolean parent) {
        diskUsage.setCaschedDiskUsageBuilds(getBuildsDiskUsage(null, null, false));
        diskUsage.setCachedDiskUsageCustomWorkspaces(getAllCustomOrNonSlaveWorkspaces(false));
        diskUsage.setCachedDiskUsageNotLoadedJobs(getAllDiskUsageNotLoadedJobs(false));
        diskUsage.setCachedDiskUsageWithoutBuilds(getAllDiskUsageWithoutBuilds(false));
        diskUsage.setCachedDiskUsageWorkspaces(getAllDiskUsageWorkspace(false));
        if(group instanceof Item){
            Item item = (Item) group;
            if(item.getParent() != null  && parent){
                DiskUsageUtil.getItemGroupAction(item.getParent()).actualizeCachedData();
            }
        }
    }

    @Override
    public Long getAllDiskUsageWithoutBuilds(boolean cached) {
        if(cached){
            return diskUsage.getCachedDiskUsageWithoutBuilds();
        }
        Long size = 0L;
        if(group instanceof AbstractProject){
            AbstractProject project = (AbstractProject) group;
            size += project.getAction(ProjectDiskUsageAction.class).getDiskUsage().getDiskUsageWithoutBuilds();
        }
        for(Item item : getItems()){
                DiskUsageItemAction action = DiskUsageUtil.getDiskUsageItemAction(item);
                size += action.getAllDiskUsageWithoutBuilds(cached);
        }
        diskUsage.setCachedDiskUsageWithoutBuilds(size);
        return size;
    }
    
    public boolean hasAdministrativePermission(){
        return Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER);
    }

    @Override
    public void actualizeCachedBuildsData() {
        diskUsage.setCaschedDiskUsageBuilds(getBuildsDiskUsage(null, null, false));
        if(group instanceof Item){
            Item item = (Item) group;
            if(item.getParent() != null){
                DiskUsageUtil.getItemGroupAction(item.getParent()).actualizeCachedBuildsData();
            }
        }
    } 

    @Override
    public void actualizeCachedWorkspaceData() {
        diskUsage.setCachedDiskUsageWorkspaces(getAllDiskUsageWorkspace(false));
        if(group instanceof Item){
            Item item = (Item) group;
            if(item.getParent() != null){
                DiskUsageUtil.getItemGroupAction(item.getParent()).actualizeCachedWorkspaceData();
            }
        }
    }

    @Override
    public void actualizeCachedNotCustomWorkspaceData() {
        diskUsage.setCachedDiskUsageCustomWorkspaces(getAllCustomOrNonSlaveWorkspaces(false));
        if(group instanceof Item){
            Item item = (Item) group;
            if(item.getParent() != null){
                DiskUsageUtil.getItemGroupAction(item.getParent()).actualizeCachedNotCustomWorkspaceData();
            }
        }
    }

    @Override
    public void actualizeCachedJobWithoutBuildsData() {
        diskUsage.setCachedDiskUsageWithoutBuilds(getAllDiskUsageWithoutBuilds(false));
        if(group instanceof Item){
            Item item = (Item) group;
            if(item.getParent() != null){
                DiskUsageUtil.getItemGroupAction(item.getParent()).actualizeCachedJobWithoutBuildsData();
            }
        }
    }


    public void actualizeCachedNotLoadedJobsData() {
        diskUsage.setCachedDiskUsageNotLoadedJobs(getAllDiskUsageNotLoadedJobs(false));
        if(group instanceof Item){
            Item item = (Item) group;
            if(item.getParent() != null){
                DiskUsageUtil.getItemGroupAction(item.getParent()).actualizeCachedNotLoadedJobsData();
            }
        }
    }

    @Override
    public void actualizeAllCachedDate() {
        actualizeCachedNotLoadedJobsData();
        actualizeCachedJobWithoutBuildsData();
        actualizeCachedNotCustomWorkspaceData();
        actualizeCachedWorkspaceData();
        actualizeCachedBuildsData();
    }
    
    
}
