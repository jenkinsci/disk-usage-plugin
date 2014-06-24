/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Run;
import hudson.model.TransientBuildActionFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageBuildActionFactory extends TransientBuildActionFactory{
    
    @Override
    public Collection<? extends Action> createFor(Run target) {
        if (target instanceof AbstractBuild){
            AbstractBuild build = (AbstractBuild) target;
            BuildDiskUsageAction action = build.getAction(BuildDiskUsageAction.class);
            if (action!=null){
                build.getActions().remove(action);
            }
            return new ArrayList<Action>(Collections.singleton(new BuildDiskUsageAction(build)));
        }
        return Collections.EMPTY_LIST;   
    }
}
