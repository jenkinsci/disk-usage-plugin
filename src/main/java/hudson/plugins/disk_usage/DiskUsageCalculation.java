package hudson.plugins.disk_usage;

import antlr.ANTLRException;
import hudson.model.AsyncAperiodicWork;
import hudson.scheduler.CronTab;
import hudson.triggers.Trigger;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.logging.Level;

/**
 *
 * @author lucinka
 */
public abstract class DiskUsageCalculation extends AsyncAperiodicWork{              
    
    public DiskUsageCalculation(String name){        
        super(name); 
    }
    
    public abstract boolean isExecuting();
    
    public String getThreadName(){
        return name +" thread";
    }
    
    public abstract DiskUsageCalculation getLastTask();
    
    public long scheduledLastInstanceExecutionTime(){
        if(getLastTask()==null || getLastTask()==this){
            return super.scheduledExecutionTime();
        }
        return getLastTask().scheduledExecutionTime();
    }
    
    @Override
    public long getInitialDelay(){
        return getRecurrencePeriod();
    } 
    
    public void reschedule(){
        if(getLastTask()==null){
            cancel();
        }
        else{
            getLastTask().cancel();   
        }
        Trigger.timer.purge();
        Trigger.timer.schedule(getNewInstance(), getRecurrencePeriod());
    }
    
    public abstract CronTab getCronTab() throws ANTLRException;
    
    @Override
    public long getRecurrencePeriod() {
        try {
            CronTab tab = getCronTab();
            GregorianCalendar now = new GregorianCalendar();
            Calendar nextExecution = tab.ceil(now.getTimeInMillis());
            long period = nextExecution.getTimeInMillis() - now.getTimeInMillis();
            if(nextExecution.getTimeInMillis() - now.getTimeInMillis()<=60000)
                period = period + 60000l; //add one minute to not shedule it during one minute one than once
            return period;           
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
            //it should not happen
            return 1000*60*6;
        }
    }
    
}
