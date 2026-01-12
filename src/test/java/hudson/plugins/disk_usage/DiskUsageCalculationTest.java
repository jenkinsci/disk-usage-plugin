/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.disk_usage;

import static org.junit.jupiter.api.AssertionFailureBuilder.assertionFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.util.Timer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 *
 * @author lucinka
 */
public class DiskUsageCalculationTest {

    /**
     * Depends on test testReschedule() - if testReschedule fails this test probably will fail too.
     * 
     * see @testReschedule()
     */
    @Test
    void testScheduledExecutionTime() {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.add(Calendar.MINUTE, 10);
        int minute = calendar.get(Calendar.MINUTE);
        //  attribute currentTask should have value calculation
        TestDiskUsageCalculation calculation = new TestDiskUsageCalculation(minute + " * * * *");
        if(calculation.getLastTask() != null) {
            // should not be any, but if cancel;
            calculation.getLastTask().cancel();
        }
        long expectedNextExecution = calendar.getTimeInMillis();
        assertCloseEnough(0, calculation.scheduledLastInstanceExecutionTime(), 60000, "Scheduled time of disk usage calculation should 0, because calculation is not scheduled");
        Timer.get().schedule(calculation.getNewInstance(), calculation.getRecurrencePeriod(), TimeUnit.MILLISECONDS);
        assertCloseEnough(expectedNextExecution, calculation.scheduledLastInstanceExecutionTime(), 60000, "Scheduled time of disk usage calculation should be in 10 minutes");

        // scheduled time should be changed if configuration of cron is changed
        calendar.add(Calendar.MINUTE, 10);
        minute = calendar.get(Calendar.MINUTE);
        calculation.setCron(minute + " * * * *");
        calculation.reschedule();
        expectedNextExecution = calendar.getTimeInMillis();
        assertCloseEnough(expectedNextExecution, calculation.scheduledLastInstanceExecutionTime(), 120_000, "Scheduled time of disk usage calculation should be changed");

    }

    @Test
    void testGetRecurrencePeriod() {
        GregorianCalendar calendar = new GregorianCalendar();

        // for minutes
        int minute = calendar.get(Calendar.MINUTE);
        minute = minute + 2;
        calendar.add(Calendar.MINUTE, 2);
        minute = calendar.get(Calendar.MINUTE);
        TestDiskUsageCalculation calculation = new TestDiskUsageCalculation(minute + " * * * *");
        long period = calculation.getRecurrencePeriod();
        long expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis();
        assertCloseEnough(expectedPeriod, period, 60000, "Disk usage calculation should be executed accurately in 2 minutes");

        // for hours
        calendar = new GregorianCalendar();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        calendar.add(Calendar.HOUR_OF_DAY, 2); // add 2 hours
        hour = calendar.get(Calendar.HOUR_OF_DAY);
        calendar.set(Calendar.MINUTE, 0);
        calculation = new TestDiskUsageCalculation("0 " + hour + " * * *");
        period = calculation.getRecurrencePeriod();
        expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis();
        assertCloseEnough(expectedPeriod, period, 60000, "Disk usage calculation should be executed accurately in 2 hours.");

        // for days
        calendar = new GregorianCalendar();
        calendar.add(Calendar.DAY_OF_MONTH, 2);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        calculation = new TestDiskUsageCalculation(calendar.get(Calendar.MINUTE) + " " + calendar.get(Calendar.HOUR_OF_DAY) + " " + day + " * *");
        period = calculation.getRecurrencePeriod();
        expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis();
        assertCloseEnough(expectedPeriod, period, 60000, "Disk usage calculation should be executed accurately in 2 days.");

        // for months
        calendar = new GregorianCalendar();
        calendar.add(Calendar.MONTH, 2);
        int month = calendar.get(Calendar.MONTH) + 1; // months are indexed from 0
        calculation = new TestDiskUsageCalculation(calendar.get(Calendar.MINUTE) + " " + calendar.get(Calendar.HOUR_OF_DAY) + " " + calendar.get(Calendar.DAY_OF_MONTH) + " " +  month + " *");
        period = calculation.getRecurrencePeriod();
        expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis();
        calendar.setTimeInMillis(System.currentTimeMillis() + period);
        assertCloseEnough(expectedPeriod, period, 60000, "Disk usage calculation should be executed accurately in 2 months.");

        // day of week
        calendar = new GregorianCalendar();
        calendar.add(Calendar.DAY_OF_WEEK, 2);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        calculation = new TestDiskUsageCalculation(calendar.get(Calendar.MINUTE) + " " + calendar.get(Calendar.HOUR_OF_DAY) + " " + calendar.get(Calendar.DAY_OF_MONTH) + " " +  (calendar.get(Calendar.MONTH) + 1) + " " + dayOfWeek);
        period = calculation.getRecurrencePeriod();
        expectedPeriod = calendar.getTimeInMillis() - System.currentTimeMillis();
        calendar.setTimeInMillis(System.currentTimeMillis() + period);
        assertCloseEnough(expectedPeriod, period, 60000, "Disk usage calculation should be executed accurately in 2 months.");

    }


    /**
     * Depends on test testScheduledExecutionTime() - if testReschedule fails this test probably will fail too.
     * <p>
     * see @testScheduledExecutionTime()
     */
    @Test
    void testReschedule() {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.add(Calendar.MINUTE, 10);
        int minute = calendar.get(Calendar.MINUTE);
        //  attribute currentTask should have value calculation
        TestDiskUsageCalculation calculation = (TestDiskUsageCalculation) new TestDiskUsageCalculation(minute + " * * * *").getNewInstance();
        Timer.get().schedule(calculation, calculation.getRecurrencePeriod(), TimeUnit.MILLISECONDS); // schedule it;
        calendar.add(Calendar.MINUTE, 10);
        minute = calendar.get(Calendar.MINUTE);
        calculation.setCron(minute + " * * * *");
        calculation.reschedule(); // should cancel this calculation and schedule new instance

        assertCloseEnough(calendar.getTimeInMillis(), calculation.scheduledLastInstanceExecutionTime(), 60000, "A new calculation should be scheduled with a new scheduled time.");

    }

    @Test
    void testTaskIsScheduledOnlyOneTimesPerMinute() throws Exception {
        //  attribute currentTask should have value calculation
        List<TestDiskUsageCalculation> scheduledInstances = new ArrayList<>();
        TestDiskUsageCalculation calculation = (TestDiskUsageCalculation) new TestDiskUsageCalculation("* * * * *").getNewInstance();
        TestDiskUsageCalculation.startLoadInstancesHistory(scheduledInstances);

        final var thread = new Thread(() -> {
            try {
                calculation.doRun();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        Thread.sleep(Duration.of(1, ChronoUnit.MINUTES).toMillis());
        thread.join();
        assertEquals(1, scheduledInstances.size(), "Method getRecurencePeriod should not able to schedule more than 1 task in 1 minute");
        TestDiskUsageCalculation.stopLoadInstancesHistory();
    }

    static void assertCloseEnough(long expected, long actual, int delta, @Nullable String message) {
        if (!((expected == actual) || Math.abs(expected - actual) <= delta)) {
            assertionFailure() //
                    .message(message) //
                    .expected(expected) //
                    .actual(actual) //
                    .buildAndThrow();
        }
    }
}
