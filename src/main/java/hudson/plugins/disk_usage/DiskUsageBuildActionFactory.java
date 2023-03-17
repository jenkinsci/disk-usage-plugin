/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;

/**
 *
 * @author Lucie Votypkova
 */
@Extension
public class DiskUsageBuildActionFactory extends TransientActionFactory<AbstractBuild> {

    @Override
    public Class type() {
        return AbstractBuild.class;
    }

    @Override
    public Collection<? extends Action> createFor(AbstractBuild t) {
        return new ArrayList<Action>(Collections.singleton(new BuildDiskUsageAction(t)));
    }
}
