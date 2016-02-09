/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.ItemGroup;
import hudson.plugins.disk_usage.unused.DiskUsageItemGroup;
import java.util.ArrayList;
import java.util.Collection;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageTransientActionFactory extends TransientActionFactory{

    @Override
    public Class type() {
        return ItemGroup.class;
    }

    @Override
    public Collection createFor(Object t) {
        ArrayList list = new ArrayList<Action>();
        if(t instanceof ItemGroup){
            DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
            DiskUsageItemGroup usage = plugin.getDiskUsageItemGroup((ItemGroup)t);
            list.add(new DiskUsageItemGroupAction(usage));
        }
        return list;
    }
    
}
