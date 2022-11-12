/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import hudson.triggers.Trigger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jenkins.util.Timer;
import junit.framework.TestCase;
import org.junit.Test;

/**
 *
 * @author lucinka
 */
public class DiskUsageCalculationTest extends TestCase{

    /**
     * Depends on test testReschedule() - if testReshedule fails this test probably will fail too.
     * 
     * see @testReschedule()
     */
    @Test
    public void testScheduledExecutionTime() throws Exception{
       // Trigger.timer = new Timer("Jenkins cron thread"); // it should be enought there is no need to start Jenkins
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.add(Calendar.MINUTE, 10);
        int minute = calendar.get(Calendar.MINUTE);
        //  attribut currentTask should have value calculation
        TestDiskUsageCalculation calculation = new TestDiskUsageCalculation(minute + " * * * *", false);
        if(calculation.getLastTask()!=null){
            //should not be any, but if cancel;
            calculation.getLastTask().cancel();
        }
        Long expectedNextExecution = calendar.getTimeInMillis();
        assertEquals("Scheduled time of disk usage calculation should 0, because calculation is not scheduled", 0, calculation.scheduledLastInstanceExecutionTime(), 60000);
        Timer.get().schedule(calculation.getNewInstance(), calculation.getRecurrencePeriod(), TimeUnit.MILLISECONDS);
        assertEquals("Scheduled time of disk usage calculation should be in 10 minutes", expectedNextExecution, calculation.scheduledLastInstanceExecutionTime(), 60000);
        
        //scheduled time should be changed if configuration of cron is changed
        calendar.add(Calendar.MINUTE, 10);
        minute = calendar.get(Calendar.MINUTE);
        calculation.setCron(minute + " * * * *");
        calculation.reschedule();
        expectedNextExecution = calendar.getTimeInMillis();
        assertEquals("Scheduled time of disk usage calculation should be changed", expectedNextExecution, calculation.scheduledLastInstanceExecutionTime(), 60000);
        
    }
    
    @Test
    public void testGetRecurrencePeriod(){
        GregorianCalendar calendar = new GregorianCalendar();
        
        //for minutes
        int minute = calendar.get(Calendar.MINUTE);
        minute = minute + 2;
        calendar.add(Calendar.MINUTE, 2);
        minute = calendar.get(Calendar.MINUTE);
        TestDiskUsageCalculation calculation = new TestDiskUsageCalculation(minute + " * * * *", false);
        Long period = calculation.getRecurrencePeriod();
        Long expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis();
        assertEquals("Disk usage calculation should be executed accurately in 2 minutes", expectedPeriod, period, 60000);
        
        //for hours
        calendar = new GregorianCalendar();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        calendar.add(Calendar.HOUR_OF_DAY, 2); //add 2 hours
        hour = calendar.get(Calendar.HOUR_OF_DAY);
        calendar.set(Calendar.MINUTE, 0);
        calculation = new TestDiskUsageCalculation("0 " + hour + " * * *", false);
        period = calculation.getRecurrencePeriod();
        expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis();
        assertEquals("Disk usage calculaion should be executed accurately in 2 hours.", expectedPeriod, period, 60000);
        
        //for days
        calendar = new GregorianCalendar();
        calendar.add(Calendar.DAY_OF_MONTH, 2);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        calculation = new TestDiskUsageCalculation(calendar.get(Calendar.MINUTE) + " " + calendar.get(Calendar.HOUR_OF_DAY) + " " + day + " * *", false);
        period = calculation.getRecurrencePeriod();    
        expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis();
        assertEquals("Disk usage calculaion should be executed accurately in 2 days.", expectedPeriod, period, 60000);
     
        //for months
        calendar = new GregorianCalendar();
        calendar.add(Calendar.MONTH, 2);
        int month = calendar.get(Calendar.MONTH) + 1; //months are indexed from 0
        calculation = new TestDiskUsageCalculation(calendar.get(Calendar.MINUTE) + " " + calendar.get(Calendar.HOUR_OF_DAY) + " " + calendar.get(Calendar.DAY_OF_MONTH) + " " +  month + " *", false);
        period = calculation.getRecurrencePeriod(); 
        expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis(); 
        calendar.setTimeInMillis(System.currentTimeMillis() + period);
        assertEquals("Disk usage calculaion should be executed accurately in 2 months.", expectedPeriod, period, 60000);
     
        //day of week
        calendar = new GregorianCalendar();
        calendar.add(Calendar.DAY_OF_WEEK, 2);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) -1; 
        calculation = new TestDiskUsageCalculation(calendar.get(Calendar.MINUTE) + " " + calendar.get(Calendar.HOUR_OF_DAY) + " " + calendar.get(Calendar.DAY_OF_MONTH) + " " +  (calendar.get(Calendar.MONTH) + 1) + " " + dayOfWeek, false);
        period = calculation.getRecurrencePeriod(); 
        expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis(); 
        calendar.setTimeInMillis(System.currentTimeMillis() + period);
        assertEquals("Disk usage calculaion should be executed accurately in 2 months.", expectedPeriod, period, 60000);
     
    }
    
    
    /**
     * Depends on test testScheduledExecutionTime() - if testReshedule fails this test probably will fail too.
     * 
     * see @testScheduledExecutionTime()
     */
    @Test
    public void testReschedule() throws Exception{
        //Trigger.timer = new Timer("Jenkins cron thread");
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.add(Calendar.MINUTE, 10);
        int minute = calendar.get(Calendar.MINUTE);
        //  attribut currentTask should have value calculation
        TestDiskUsageCalculation calculation = (TestDiskUsageCalculation) new TestDiskUsageCalculation(minute + " * * * *", true).getNewInstance();       
        Timer.get().schedule(calculation, calculation.getRecurrencePeriod(), TimeUnit.MILLISECONDS); //schedule it;
        calendar.add(Calendar.MINUTE, 10);
        minute = calendar.get(Calendar.MINUTE);
        calculation.setCron(minute + " * * * *");
        calculation.reschedule(); // should cancel this calculation and schedule new instance
//        try{
//            System.out.println("new schedule");
//            calculation.doRun();
//        }
//        catch(IllegalArgumentException e){
//                fail("Calculation should be canceled.");
//        }
        assertEquals("A new calculation should be scheduled with a new scheduled time.", calendar.getTimeInMillis(), calculation.scheduledLastInstanceExecutionTime(), 60000);
        
    }
    
    @Test
    public void testTaskIsScheduledOnlyOneTimesPerMinute() throws Exception{
      //  Trigger.timer = new Timer("Jenkins cron thread");
        //  attribut currentTask should have value calculation
        List<TestDiskUsageCalculation> scheduledInstances = new ArrayList<TestDiskUsageCalculation>();
        TestDiskUsageCalculation calculation = (TestDiskUsageCalculation) new TestDiskUsageCalculation("* * * * *", false).getNewInstance();       
        TestDiskUsageCalculation.startLoadInstancesHistory(scheduledInstances);
        GregorianCalendar calendar = new GregorianCalendar();
        int seconds = calendar.get(Calendar.SECOND);
        boolean firstLoop = true;
        while((seconds>55 && seconds<59) || seconds==0){ //have enought time for measure in current minute
            if(firstLoop){
                System.out.println("Waiting for appropriate time ");
                firstLoop = false;
            }
            else{
                System.out.println(".");
            }
            Thread.sleep(1000); 
            seconds = calendar.get(Calendar.SECOND);
        }      
        calculation.doRun();
        Thread.sleep(2000);
        assertEquals("Method getRecurencePeriod should not able to schedule more than 1 task in 1 minute", 1, scheduledInstances.size());
        TestDiskUsageCalculation.stopLoadInstancesHistory(); 
    }
    
}
