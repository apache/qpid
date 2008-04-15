package org.apache.qpid.util;

import junit.framework.TestCase;

import java.util.Random;

import org.apache.qpid.SerialException;

/**
 *Junit tests for the Serial class 
 */
public class SerialTest extends TestCase
{

    /**
     * The simplest meaningful serial number space has SERIAL_BITS == 2.  In
     * this space, the integers that make up the serial number space are 0,
     * 1, 2, and 3.  That is, 3 == 2^SERIAL_BITS - 1.
     *
     * In this space, the largest integer that it is meaningful to add to a
     * sequence number is 2^(SERIAL_BITS - 1) - 1, or 1.
     *
     * Then, as defined 0+1 == 1, 1+1 == 2, 2+1 == 3, and 3+1 == 0.
     * Further, 1 > 0, 2 > 1, 3 > 2, and 0 > 3.  It is undefined whether
     * 2 > 0 or 0 > 2, and whether 1 > 3 or 3 > 1.
     */
    public void testTrivialSample()
    {
        Serial serial = new Serial(2);
        assertEquals( serial.increment(0, 1), 1);
        assertEquals( serial.increment(1, 1), 2);
        assertEquals( serial.increment(2, 1), 3);
        assertEquals( serial.increment(3, 1), 0);
        try
        {
            serial.increment(4, 1);
            fail("IllegalArgumentException was not trhown");
        }
        catch (IllegalArgumentException e)
        {
           // expected
        }
        try
        {
            assertTrue( serial.compare(1, 0) > 0);
            assertTrue( serial.compare(2, 1) > 0);
            assertTrue( serial.compare(3, 2) > 0);
            assertTrue( serial.compare(0, 3) > 0);
            assertTrue( serial.compare(0, 1) < 0);
            assertTrue( serial.compare(1, 2) < 0);
            assertTrue( serial.compare(2, 3) < 0);
            assertTrue( serial.compare(3, 0) < 0);
        }
        catch (SerialException e)
        {
            fail("Unexpected exception " + e);
        }
        try
        {
            serial.compare(2, 0);
            fail("AMQSerialException not thrown as expected");
        }
        catch (SerialException e)
        {
           // expected
        }
        try
        {
            serial.compare(0, 2);
            fail("AMQSerialException not thrown as expected");
        }
        catch (SerialException e)
        {
           // expected
        }
        try
        {
            serial.compare(3, 1);
            fail("AMQSerialException not thrown as expected");
        }
        catch (SerialException e)
        {
           // expected
        }
        try
        {
            serial.compare(3, 1);
            fail("AMQSerialException not thrown as expected");
        }
        catch (SerialException e)
        {
           // expected
        }
    }

    /**
     * Test the first Corollary of RFC 1982
     * For any sequence number s and any integer n such that addition of n
     * to s is well defined, (s + n) >= s.  Further (s + n) == s only when
     * n == 0, in all other defined cases, (s + n) > s.
     * strategy:
     * Create a serial number with 32 bits and check in a loop that adding integers
     * respect the corollary
     */
    public void testCorollary1()
    {
        Serial serial = new Serial(32);
        Random random = new Random();
        long number = random.nextInt((int) Math.pow(2.0 , 32.0) - 1);
        for(int i = 1; i<= 10000; i++ )
        {
           long nextInt = random.nextInt((int) Math.pow(2.0 , 32.0) - 1);
           long inc = serial.increment(number, nextInt);
            int res =0;
            try
            {
                res=serial.compare(inc, number);
            }
            catch (SerialException e)
            {
                fail("un-expected exception " + e);
            }
            if( res < 1 )
            {
               fail("Corollary 1 violated " + number + " + " + nextInt + " < " + number);
            }
            else if( res == 0 && nextInt > 0)
            {
               fail("Corollary 1 violated " + number + " + " + nextInt + " = " + number);
            }
        }
    }

    
}
