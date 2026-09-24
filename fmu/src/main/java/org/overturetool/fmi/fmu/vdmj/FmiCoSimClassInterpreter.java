package org.overturetool.fmi.fmu.vdmj;

import com.fujitsu.vdmj.in.INNode;
import com.fujitsu.vdmj.in.definitions.INClassList;
import com.fujitsu.vdmj.runtime.ClassInterpreter;
import com.fujitsu.vdmj.scheduler.MainThread;
import com.fujitsu.vdmj.scheduler.ResourceScheduler;
import com.fujitsu.vdmj.scheduler.RunState;
import com.fujitsu.vdmj.scheduler.SystemClock;
import com.fujitsu.vdmj.tc.definitions.TCClassList;
import com.fujitsu.vdmj.util.Utils;

import java.io.Serial;

/**
 * ClassInterpreter subclass that uses the co-sim scheduler.
 */
public class FmiCoSimClassInterpreter extends ClassInterpreter {

    public final Object stepLock = new Object();
    private long nextSimulationStop = 0L;
    private long currentTime;
    private long nextSchedulableActionTime = 0L;
    private boolean interpreterRunning = false;
    private Thread interpreterThread = null;

    public FmiCoSimClassInterpreter(INClassList executableClasses, TCClassList checkedClasses) throws Exception {
        super(executableClasses, checkedClasses);
        this.scheduler = new FmiCoSimResourceScheduler();
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public long getNextSchedulableActionTime() {
        return nextSchedulableActionTime;
    }

    private void notifyAndWaitForStepLock() {

        stepLock.notifyAll();
        try {
            stepLock.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    public void advance(long internalVdmClockTime) {
        nextSimulationStop = internalVdmClockTime;
        interpreterRunning = true;
        notifyAndWaitForStepLock();
        currentTime = SystemClock.getWallTime();
        interpreterRunning = false;
    }

    public void start() {
        try {
            long before = System.currentTimeMillis();
            Utils.mapperStats(before, INNode.MAPPINGS);


            this.init();

            this.interpreterThread = new Thread(() -> {
                try {
                    synchronized (stepLock) {
                        while (!interpreterRunning) {
                            try {
                                stepLock.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                    this.execute("new World().run()");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            this.interpreterThread.start();
            long after = System.currentTimeMillis();
            Utils.mapperStats(after - before, INNode.MAPPINGS);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        if (this.interpreterThread != null && this.interpreterThread.isAlive()) {
            this.interpreterThread.interrupt();
            try {
                this.interpreterThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Custom scheduler that gates execution to FMI step boundaries.
     */
    private class FmiCoSimResourceScheduler extends ResourceScheduler {
        @Serial
        private static final long serialVersionUID = 1L;

        private java.util.List<com.fujitsu.vdmj.scheduler.Resource> getResources() throws Exception {
            java.lang.reflect.Field field = ResourceScheduler.class.getDeclaredField("resources");
            field.setAccessible(true);
            //noinspection unchecked
            return (java.util.List<com.fujitsu.vdmj.scheduler.Resource>) field.get(this);
        }

        @Override
        public void start(MainThread main) {
            try {
                boolean idle = true;

                do {
                    long minstep = Long.MAX_VALUE;
                    idle = true;
                    java.util.List<com.fujitsu.vdmj.scheduler.Resource> resources = getResources();

                    for (com.fujitsu.vdmj.scheduler.Resource resource : resources) {
                        if (resource.reschedule()) {
                            idle = false;
                        } else {
                            long d = resource.getMinimumTimestep();
                            if (d < minstep) {
                                minstep = d;
                            }
                        }
                    }

                    if (idle && minstep >= 0 && minstep < Long.MAX_VALUE) {
                        nextSchedulableActionTime = SystemClock.getWallTime() + minstep;
                        synchronized (stepLock) {
                            if (canAdvanceTime(nextSchedulableActionTime)) {
//                                System.out.println("\t\tAdvancing VDM time by " + minstep + " to " + nextSchedulableActionTime);
                                SystemClock.advance(minstep);
                                for (com.fujitsu.vdmj.scheduler.Resource resource : resources) {
                                    resource.advance();
                                }
                            }
                        }


                        idle = false;
                    }

                } while (!idle && main.getRunState() != RunState.COMPLETE && main.getException() == null);
            } catch (Exception e) {
                throw new RuntimeException("Scheduler error: " + e.getMessage(), e);
            }
        }


        private boolean canAdvanceTime(long newTime) {
            if (newTime <= nextSimulationStop) {
                return true;
            } else {
                notifyAndWaitForStepLock();

                if (newTime <= nextSimulationStop) {
                    return true;
                } else {

                    return false;
                }
            }
        }
    }

}
