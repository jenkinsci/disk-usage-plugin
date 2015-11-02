/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.ItemGroup;
import java.util.ArrayList;
import java.util.Collection;
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
            list.add(new DiskUsageItemGroupAction((ItemGroup)t));
        }
        return list;
    }
    
}
