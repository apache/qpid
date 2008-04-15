package org.apache.qpid.util;

import org.apache.qpid.SerialException;

/**
 * This class provides basic
 * serial number arithmetic as defined in RFC 1982.
 */

public class Serial
{
    private long _maxIncrement;
    private long _max;
    private long _maxComparison;

    public Serial(long serialbits)
    {
        if( serialbits < 2)
        {
            throw new IllegalArgumentException("Meaningful serial number space has SERIAL_BITS >= 2, wrong value "
                    + serialbits);
        }
        _max = (long) Math.pow(2.0 , serialbits) - 1;
        _maxIncrement = (long) Math.pow(2.0, serialbits - 1) - 1;
        _maxComparison = (long) Math.pow(2.0, serialbits -1);
    }

    /**
     * Compares two numbers using serial arithmetic.
     *
     * @param serial1 The first serial number
     * @param serial2 The second serial number
     * @return 0 if the 2 serials numbers are equal, a positive number if serial1 is greater
     *         than serial2, and a negative number if serial2 is greater than serial1.
     * @throws IllegalArgumentException serial1 or serial2 is out of range
     * @throws SerialException serial1 and serial2 are not comparable.
     */
    public int compare(long serial1, long serial2) throws IllegalArgumentException, SerialException
    {
        int result;
        if (serial1 < 0 || serial1 > _max)
        {
            throw new IllegalArgumentException(serial1 + " out of range");
        }
        if (serial2 < 0 || serial2 > _max)
        {
            throw new IllegalArgumentException(serial2 + " out of range");
        }        
        double diff;
        if( serial1 < serial2 )
        {
           diff = serial2 - serial1; 
           if( diff < _maxComparison )
           {
             result = -1;
           }
           else if ( diff > _maxComparison )
           {
               result = 1;
           }
           else
           {
               throw new SerialException("Cannot compare " + serial1 + " and " + serial2);
           }
        }
        else if( serial1 > serial2 )
        {
           diff = serial1 - serial2;
           if( diff > _maxComparison )
           {
             result = -1;
           }
           else if( diff < _maxComparison )
           {
               result = 1;
           }           
           else
           {
               throw new SerialException("Cannot compare " + serial1 + " and " + serial2);
           }
        }
        else
        {
            result = 0;
        }
        return result;
    }

 
    /**
     * Increments a serial numbers by the addition of a positive integer n,
     * Serial numbers may be incremented by the addition of a positive
     * integer n, where n is taken from the range of integers
     * [0 .. (2^(SERIAL_BITS - 1) - 1)].  For a sequence number s, the
     * result of such an addition, s', is defined as
     *              s' = (s + n) modulo (2 ^ SERIAL_BITS)
     * @param serial The serila number to be incremented
     * @param n      The integer to be added to the serial number
     * @return The incremented serial number
     * @throws IllegalArgumentException serial number or n is out of range
     */
    public long increment(long serial, long n) throws IllegalArgumentException
    {
        if (serial < 0 || serial > _max)
        {
            throw new IllegalArgumentException("Serial number: " + serial + " is out of range");
        }
        if( n < 0 || n > _maxIncrement )
        {
            throw new IllegalArgumentException("Increment: " + n + " is out of range");
        }
        long result = serial + n;
        // apply modulo (2 ^ SERIAL_BITS)
        if(result > _max)
        {
            result = result - _max - 1;
        }
        return result;
    }

}
