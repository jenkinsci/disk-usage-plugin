/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class ProjectConfigListener extends SaveableListener{

    @Override
   public void onChange(Saveable object, XmlFile file){
       if(object instanceof AbstractBuild){
           AbstractBuild build = (AbstractBuild) object;
           ProjectDiskUsageAction action = build.getProject().getAction(ProjectDiskUsageAction.class);
           DiskUsageBuildInformation info = action.getDiskUsage().getDiskUsageBuildInformation(build.getId());
           if(info==null){
               action.getDiskUsage().addBuild(build);
           }
           else{
               if(info.isLocked()!= build.isKeepLog()){
                   info.setLockState(build.isKeepLog());
                   action.getDiskUsage().save();
               }
           }
       }
   }
}
