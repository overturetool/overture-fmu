package com.fujitsu.vdmj.scheduler;

import com.fujitsu.vdmj.scheduler.SystemClock;

public class VdmSystemTime extends SystemClock {
    /**
     * Utility method to convert the internal time to the given unit.
     *
     * @param unit
     *            The unit to convert the internal time to
     * @param internalTime
     *            The internal time
     * @return The internal time representation of the parameter
     */
    public static Double internalToTime(TimeUnit unit, long internalTime)
    {
        return Math.round(internalTime * TimeUnit.nanosecond.getValue()
                / unit.getValue() * PRECISION)
                / PRECISION;
    }

    /**
     * Utility method to convert the internal time to the given unit.
     *
     * @param unit
     *            The unit to convert the internal time to
     * @return The internal time representation of the parameter
     */
    public static Double internalToTime(TimeUnit unit)
    {
        return internalToTime(unit, getWallTime());
    }
}
