/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import antlr.ANTLRException;
import hudson.model.AsyncAperiodicWork;
import hudson.scheduler.CronTab;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;

/**
 *
 * @author lucinka
 */
public abstract class DiskUsageCalculation extends AsyncAperiodicWork {

    private boolean cancelled;

    public DiskUsageCalculation(String name) {
        super(name);
    }

    public boolean isExecuting() {
        for(Thread t: Thread.getAllStackTraces().keySet()) {
            if(t.getName().equals(getThreadName())) {
                return t.isAlive() && !t.isInterrupted();
            }
        }
        return false;
    }

    public boolean isExecutingMoreThenOneTimes() {
        int count = 0;
        for(Thread t: Thread.getAllStackTraces().keySet()) {
            if(t.getName().equals(getThreadName())) {
                if(t.isAlive() && !t.isInterrupted()) {
                    count++;
                }
            }
        }
        return count > 1;
    }

    public String getThreadName() {
        return name + " thread";
    }

    public abstract DiskUsageCalculation getLastTask();

    public long scheduledLastInstanceExecutionTime() {
        try {
            if(getLastTask() == null || getLastTask().isCancelled()) { // not scheduled
                return 0L;
            }
            long time = getCronTab().ceil(new GregorianCalendar().getTimeInMillis()).getTimeInMillis();
            if(time < new GregorianCalendar().getTimeInMillis()) {
                return 0;
            }
            return time;

        } catch (ANTLRException ex) {
            Logger.getLogger(DiskUsageCalculation.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }
    }

    @Override
    public long getInitialDelay() {
        return getRecurrencePeriod();
    }

    @Override
    public boolean cancel() {
        cancelled = true;
        final ScheduledExecutorService scheduledExecutorService = Timer.get();
        if(scheduledExecutorService instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor ex = (ScheduledThreadPoolExecutor) scheduledExecutorService;
            ex.purge();
        }
        return super.cancel();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void reschedule() {
        if(getLastTask() == null) {
            cancel();
        }
        else {
            getLastTask().cancel();
        }
        Timer.get().schedule(getNewInstance(), getRecurrencePeriod(), TimeUnit.MILLISECONDS);
    }

    public abstract CronTab getCronTab() throws ANTLRException;

    @Override
    public long getRecurrencePeriod() {
        try {
            CronTab tab = getCronTab();
            GregorianCalendar now = new GregorianCalendar();
            Calendar nextExecution = tab.ceil(now.getTimeInMillis());
            long period = nextExecution.getTimeInMillis() - now.getTimeInMillis();
            if(nextExecution.getTimeInMillis() - now.getTimeInMillis() <= 60000) {
                period = period + 60_000L; // add one minute to not shedule it during one minute one than once
            }
            return period;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
            // it should not happen
            return 1000 * 60 * 6;
        }
    }

}
