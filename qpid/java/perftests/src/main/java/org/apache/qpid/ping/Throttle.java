package org.apache.qpid.ping;

/**
 * Throttle is a helper class used in situations where a controlled rate of processing is desired. It allows a certain
 * number of operations-per-second to be defined and supplies a {@link #throttle} method that can only be called at
 * most at that rate. The first call to the throttle method will return immediately, subsequent calls will introduce
 * a short pause to fill out the remainder of the current cycle to attain the desired rate. If there is no remainder
 * left then it will return immediately.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * </table>
 *
 * @author Rupert Smith
 */
public class Throttle
{
    /** Holds the length of the cycle in nano seconds. */
    long cycleLengthNanos = 0L;

    /** Records the nano time of the last call to the throttle method. */
    long lastTimeNanos = 0L;

    /**
     * Sets up the desired rate of operation per second that the throttle method should restrict to.
     *
     * @param opsPerSecond The maximum number of calls per second that the throttle method will take.
     */
    public void setRate(int opsPerSecond)
    {
        // Calculate the length of a cycle.
        cycleLengthNanos = 1000000000 / opsPerSecond;
    }

    /**
     * Introduces a short pause to fill out any time left in the cycle since this method was last called, of length
     * defined by a call to the {@link #setRate} method.
     */
    public void throttle()
    {
        // Record the time now.
        long currentTimeNanos = System.nanoTime();

        // Check if there is any time remaining in the current cycle and introduce a short wait to fill out the
        // remainder of the cycle if needed.
        long remainingTimeNanos = cycleLengthNanos - (currentTimeNanos - lastTimeNanos);

        if (remainingTimeNanos > 0)
        {
            long milliWait = remainingTimeNanos / 1000000;
            int nanoWait = (int) (remainingTimeNanos % 1000000);

            try
            {
                Thread.currentThread().sleep(milliWait, nanoWait);
            }
            catch (InterruptedException e)
            {
                // Just ignore this?
            }
        }

        // Keep the time of the last call to this method to calculate the next cycle.
        //lastTimeNanos = currentTimeNanos;
        lastTimeNanos = System.nanoTime();
    }
}
