/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.listeners.SaveableListener;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class ProjectConfigListener extends SaveableListener{
    
//    @Override
//    public void onChange(Saveable item, XmlFile file){
//        System.out.println("save item " + item);
//        if(item instanceof AbstractProject){
//            System.out.println("builds  " + ((AbstractProject) item).getBuilds());
//           AbstractProject project = (AbstractProject) item;
//           DiskUsageProperty property = (DiskUsageProperty) project.getProperty(DiskUsageProperty.class);
//           if(property==null){
//                DiskUsageUtil.addProperty(project);
//           }
//        }
//    }
    
}
