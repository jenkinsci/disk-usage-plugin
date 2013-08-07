package hudson.plugins.disk_usage;

import hudson.Extension;
import hudson.model.*;

import java.util.Collection;
import java.util.Collections;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class DiskUsageProjectActionFactory extends TransientProjectActionFactory {

    @Override
    public Collection<? extends Action> createFor(AbstractProject job) {
        return Collections.singleton(new ProjectDiskUsageAction(job));
    }

}
