package org.apache.qpid.util;

import org.apache.qpid.SerialException;

/**
 * This class provides basic serial number comparisons as defined in
 * RFC 1982.
 */

public class Serial
{

    /**
     * Compares two numbers using serial arithmetic.
     *
     * @param s1 the first serial number
     * @param s2 the second serial number
     *
     * @return a negative integer, zero, or a positive integer as the
     * first argument is less than, equal to, or greater than the
     * second
     */
    public static final int compare(int s1, int s2)
    {
        return s1 - s2;
    }

    public static final boolean lt(int s1, int s2)
    {
        return compare(s1, s2) < 0;
    }

    public static final boolean le(int s1, int s2)
    {
        return compare(s1, s2) <= 0;
    }

    public static final boolean gt(int s1, int s2)
    {
        return compare(s1, s2) > 0;
    }

    public static final boolean ge(int s1, int s2)
    {
        return compare(s1, s2) >= 0;
    }

    public static final boolean eq(int s1, int s2)
    {
        return s1 == s2;
    }

    public static final int min(int s1, int s2)
    {
        if (lt(s1, s2))
        {
            return s1;
        }
        else
        {
            return s2;
        }
    }

    public static final int max(int s1, int s2)
    {
        if (gt(s1, s2))
        {
            return s1;
        }
        else
        {
            return s2;
        }
    }

}
