package hudson.plugins.disk_usage;

import hudson.model.AperiodicWork;
import hudson.model.TaskListener;
import hudson.scheduler.CronTab;
import java.util.List;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author lucinka
 */
public class TestDiskUsageCalculation extends BuildDiskUsageCalculationThread {

    private String cron;

    public boolean executing;

    private static List<TestDiskUsageCalculation> instancesHistory;

    private final static int maxInstances = 10;

    private static TestDiskUsageCalculation currentInstance;

    public TestDiskUsageCalculation(String cron) {
        this.cron = cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    @Override
    public CronTab getCronTab() {
        return new CronTab(cron);
    }

    public static void startLoadInstancesHistory(List<TestDiskUsageCalculation> history) {
        TestDiskUsageCalculation.instancesHistory = history;
    }

    public static void stopLoadInstancesHistory() {
        TestDiskUsageCalculation.instancesHistory = null;
    }

    @Override
    public void execute(TaskListener listener) {
        executing = true;
        try {
            Thread.sleep(10000);
        } catch (InterruptedException ignored) {
        }
        executing = false;
    }

    @Override
    public AperiodicWork getNewInstance() {
        TestDiskUsageCalculation c = new TestDiskUsageCalculation(cron);
        if(instancesHistory != null) {
            if(maxInstances <= instancesHistory.size()) {
                instancesHistory.get(0).cancel();
                instancesHistory.remove(0);
            }
            instancesHistory.add(c);
        }
        if(currentInstance != null) {
            currentInstance.cancel();
        } else {
            cancel();
        }
        currentInstance = c;
        return currentInstance;
    }

    @Override
    public DiskUsageCalculation getLastTask() {
        return currentInstance;
    }

}
