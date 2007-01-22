package org.apache.qpid.ping;

import junit.framework.Test;
import junit.framework.TestSuite;

import uk.co.thebadgerset.junit.extensions.AsymptoticTestCase;

/**
 * Tests the {@link Throttle} implementation. Test timings can be taken using this test class to confirm that the
 * throttle works as it should, and what the maximum rate is that it works reliably.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Enable test timings to be taken to confirm that the throttle works at the correct rate.
 * </table>
 *
 * @author Rupert Smith
 */
public class ThrottleTestPerf extends AsymptoticTestCase
{
    ThreadLocal<Throttle> threadSetup = new ThreadLocal<Throttle>();

    public ThrottleTestPerf(String name)
    {
        super(name);
    }

    /**
     * Compile all the tests into a test suite.
     */
    public static Test suite()
    {
        // Build a new test suite
        TestSuite suite = new TestSuite("Ping-Pong Performance Tests");

        // Run performance tests in read committed mode.
        suite.addTest(new ThrottleTestPerf("testThrottle"));

        return suite;
    }

    public void testThrottle(int opsPerSecond)
    {
        Throttle throttle = threadSetup.get();

        // Setting this on every test call won't cause any harm, convenient to use the size parameter for this.
        throttle.setRate(opsPerSecond);

        // Run the test at the throttled rate, do this for the num of opsPerSecond, then every test should take 1 second.
        for (int i = 0; i < opsPerSecond; i++)
        {
            throttle.throttle();
        }
    }

    protected void setUp()
    {
        if (threadSetup.get() == null)
        {
            threadSetup.set(new Throttle());
        }
    }
}
