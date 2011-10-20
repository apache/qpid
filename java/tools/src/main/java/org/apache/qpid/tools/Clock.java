package org.apache.qpid.tools;

/**
 * In the future this will be replaced by a Clock abstraction
 * that can utilize a realtime clock when running in RT Java.
 */

public class Clock
{
    private static Precision precision;
    private static long offset = -1;  // in nano secs

    public enum Precision
    {
        NANO_SECS, MILI_SECS;

        static Precision getPrecision(String str)
        {
            if ("mili".equalsIgnoreCase(str))
            {
                return MILI_SECS;
            }
            else
            {
                return NANO_SECS;
            }
        }
    };

    static
    {
        precision = Precision.getPrecision(System.getProperty("precision","mili"));
        //offset = Long.getLong("offset",-1);

        System.out.println("Using precision : " + precision + " and offset " + offset);
    }

    public static Precision getPrecision()
    {
        return precision;
    }

    public static long getTime()
    {
        if (precision == Precision.NANO_SECS)
        {
            if (offset == -1)
            {
                return System.nanoTime();
            }
            else
            {
                return System.nanoTime() + offset;
            }
        }
        else
        {
            if (offset == -1)
            {
                return System.currentTimeMillis();
            }
            else
            {
                return System.currentTimeMillis() + offset/convertToMiliSecs();
            }
        }
    }

    public static long convertToSecs()
    {
        if (precision == Precision.NANO_SECS)
        {
            return 1000000000;
        }
        else
        {
            return 1000;
        }
    }

    public static long convertToMiliSecs()
    {
        if (precision == Precision.NANO_SECS)
        {
            return 1000000;
        }
        else
        {
            return 1;
        }
    }
}
