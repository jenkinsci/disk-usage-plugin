/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.plugins.disk_usage.unused.DiskUsageItemGroup;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
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
    
    public DiskUsageItemGroupAction(ItemGroup group){
        this.group = group;
    }
    
    public Collection<Item> getItems(){
        return group.getItems();
    }
    
    public DiskUsageItemGroup getDiskUsageItemGroup(){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        DiskUsageItemGroup usage = plugin.getDiskUsageItemGroup(group);
        return usage;
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
    
    
    
    
    
    @Override
    public Long getAllDiskUsage() {
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        DiskUsageItemGroup usage = plugin.getDiskUsageItemGroup(group);
        Long size = usage.getDiskUsageOfNotLoadedJobs() + usage.getDiskUsageWithoutJobs();
        //loaded
        for(Item item : getItems()){
            DiskUsageItemAction action = getDiskUsageItemAction(item);
            if(action!=null){

               size += action.getAllDiskUsage();
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

    public String getAllDiskUsageInString() {
        return DiskUsageUtil.getSizeString(getAllDiskUsage());
    }

   
    public Long getDiskUsageOfItem(Item item) throws IOException{
        DiskUsageItemAction action = getDiskUsageItemAction(item);
        if(action!=null){
            return DiskUsageUtil.getItemGroupAction(group).getAllDiskUsage();
        }
        return 0L;
        
    }


    public String getDiskUsageOfItemInString(Item item) throws IOException {
        return DiskUsageUtil.getSizeString(getDiskUsageOfItem(item));
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
        DiskUsageItemAction action = getDiskUsageItemAction(item);
        if(action!=null){
            return DiskUsageUtil.getItemGroupAction((ItemGroup)item).getDiskUsageBuilds(type);
        }
        return 0L;
    }
    
    private Long getDiskUsageBuilds(String type) throws IOException{
        Long sizeLocked = 0L;
        for(Item item : getSubItems()){
            DiskUsageItemAction action = getDiskUsageItemAction(item);
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
    public Map<String,Long> getBuildsDiskUsage(Date older, Date younger) {
         Map<String,Long> usage = new HashMap<String,Long>();
         addAllBuildsDiskUsage(usage, older, younger);
         return usage;
    }
    
    //can be null
    public DiskUsageItemAction getDiskUsageItemAction(Item item){
        if(item instanceof AbstractProject) {
            AbstractProject project = (AbstractProject) item;
            return project.getAction(ProjectDiskUsageAction.class);
        }
        if(item instanceof ItemGroup){
            return DiskUsageUtil.getItemGroupAction((ItemGroup)item);
        }
        //there is not supported something else
        return null;
    }

    
    public void addAllBuildsDiskUsage(Map<String,Long> usage, Date older, Date younger) {
        for(Item item : getItems() ){
            DiskUsageItemAction action = getDiskUsageItemAction(item);
            if(action!=null){
                Map<String,Long> projectUsage = action.getBuildsDiskUsage(older, younger);
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
    
    
    public Map<String, Long> getBuildsDiskUsage(Item item, Date older, Date younger) throws IOException {
        DiskUsageItemAction action = getDiskUsageItemAction(item);
        if(action!=null){
            return getDiskUsageItemAction(item).getBuildsDiskUsage(older, younger);
        }
        return Collections.EMPTY_MAP;
    }
    
    @Override
    public Long getAllDiskUsageWorkspace(){
        Long size = 0L;
        for(Item item : getItems()){
            DiskUsageItemAction action = getDiskUsageItemAction(item);
            if(action!=null){

               size += action.getAllDiskUsageWorkspace();
            }
        }
        return size;
        
    }
    
    @Override
    public Long getDiskUsageWithoutBuilds(){
        Long size = 0L;
        for(Item item : getItems()){
            DiskUsageItemAction action = getDiskUsageItemAction(item);
            if(action!=null){
                size += action.getDiskUsageWithoutBuilds();
            }
        }
        return size;
    }
    
    public Long getDiskUsageWithoutBuilds(Item item){
        DiskUsageItemAction action = getDiskUsageItemAction(item);
        if(action!=null){
            return action.getDiskUsageWithoutBuilds();
        }
        return 0L;
    }
    
    public Long getAllDiskUsageWorkspace(Item item){
        DiskUsageItemAction action = getDiskUsageItemAction(item);
        if(action!=null){
            return action.getAllDiskUsageWorkspace();
        }
        return 0L;
    }
    
    public Long getAllCustomOrNonSlaveWorkspaces(Item item){
        DiskUsageItemAction action = getDiskUsageItemAction(item);
        if(action!=null){
            return action.getAllCustomOrNonSlaveWorkspaces();
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
    public Long getAllCustomOrNonSlaveWorkspaces(){
        Long size = 0L;
        for(Item item : getItems()){
            DiskUsageItemAction action = getDiskUsageItemAction(item);
            if(action!=null){
                size += action.getAllDiskUsageWorkspace();
            }
        }
        return size;
    }
    
    
}
