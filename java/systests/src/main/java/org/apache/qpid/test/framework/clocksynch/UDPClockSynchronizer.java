/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.test.framework.clocksynch;

import uk.co.thebadgerset.junit.extensions.util.CommandLineParser;
import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;

/**
 * UDPClockSynchronizer is a {@link ClockSynchronizer} that sends pings as UDP datagrams, and uses the following simple
 * algorithm to perform clock synchronization:
 *
 * <ol>
 * <li>Slave initiates synchronization with a Reference clock.</li>
 * <li>Slave stamps current local time on a "time request" message and sends to the Reference.</li>
 * <li>Upon receipt by Reference, Reference stamps Reference-time and returns.</li>
 * <li>Upon receipt by Slave, Slave subtracts current time from sent time and divides by two to compute latency. It
 *     subtracts current time from Reference time to determine Slave-Reference time delta and adds in the
 *     half-latency to get the correct clock delta.</li>
 * <li>The first result is immediately used to update the clock since it will get the local clock into at least
 *     the right ballpark.</li>
 * <li>The Slave repeats steps 2 through 4, 15 more times.</li>
 * <li>The results of the packet receipts are accumulated and sorted in lowest-latency to highest-latency order. The
 *     median latency is determined by picking the mid-point sample from this ordered list.</li>
 * <li>All samples above approximately 1 standard-deviation from the median are discarded and the remaining samples
 *     are averaged using an arithmetic mean.</li>
 * </ol>
 *
 * <p/>The use of UDP datagrams, instead of TCP based communication eliminates the hidden delays that TCP can introduce,
 * as it can transparently re-order or re-send packets, or introduce delays as packets are naggled.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Trigger a clock synchronziation.
 * <tr><td> Compute a clock delta to apply to the local clock.
 * <tr><td> Estimate the error in the synchronzation.
 * </table>
 */
public class UDPClockSynchronizer implements ClockSynchronizer
{
    /** The clock delta. */
    private long delta = 0L;

    /** Holds an estimate of the clock error relative to the reference clock. */
    private long epsilon = 0L;

    /** Holds the address of the reference clock. */
    private InetAddress referenceAddress;

    /** Holds the socket to communicate with the reference service over. */
    private DatagramSocket socket;

    /**
     * Creates a clock synchronizer against the specified address for the reference.
     *
     * @param address The address of the reference service.
     */
    public UDPClockSynchronizer(String address)
    {
        try
        {
            referenceAddress = InetAddress.getByName(address);
        }
        catch (UnknownHostException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * The slave side should call this to compute a clock delta with the reference.
     */
    public void synch()
    {
        try
        {
            socket = new DatagramSocket();

            // Synchronize on a single ping, to get the clock into the right ball-park.
            synch(1);

            // Synchronize on 15 pings for greater accuracy.
            synch(15);

            socket.close();
        }
        catch (SocketException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates the synchronization delta by performing the specified number of reference clock requests.
     *
     * @param n The number of reference clock request cycles to perform.
     */
    protected void synch(int n)
    {
        // Create an array of deltas by performing n reference pings.
        long[] delta = new long[n];

        for (int i = 0; i < n; i++)
        {
            delta[i] = ping();
        }

        // Reject any deltas that are larger than 1 s.d. above the median.
        long median = median(delta);
        long sd = standardDeviation(delta);

        long[] tempDeltas = new long[n];
        int count = 0;

        for (int i = 0; i < n; i++)
        {
            if (delta[i] <= (median + sd))
            {
                tempDeltas[count] = delta[i];
                count++;
            }
        }

        System.arraycopy(tempDeltas, 0, delta, 0, count);

        // Estimate the delta as the mean of the remaining deltas.
        this.delta += mean(delta);

        // Estimate the error as the standard deviation of the remaining deltas.
        this.epsilon = standardDeviation(delta);

    }

    /**
     * Performs a single reference clock request cycle and returns the estimated delta relative to the local clock.
     * This is computed as the half-latency of the requst cycle, plus the reference clock, minus the local clock.
     *
     * @return The estimated clock delta.
     */
    protected long ping()
    {
        try
        {
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length, referenceAddress, UDPClockReference.REFERENCE_PORT);

            // Start timing the request latency.
            long start = nanoTime();

            // Get the reference time.
            socket.send(packet);
            packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            ByteBuffer bbuf = ByteBuffer.wrap(packet.getData());
            long refTime = bbuf.getLong();

            // Stop timing the request latency.
            long localTime = nanoTime();
            long end = localTime - start;

            // Estimate delta as (ref clock + half-latency) - local clock.
            return ((end - start) / 2) + refTime - localTime;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the clock delta in nano seconds.
     *
     * @return The clock delta in nano seconds.
     */
    public long getDelta()
    {
        return delta;
    }

    /**
     * Gets an estimate of the clock error in nan seconds.
     *
     * @return An estimate of the clock error in nan seconds.
     */
    public long getEpsilon()
    {
        return epsilon;
    }

    /**
     * Gets the local clock time with any computed delta added in.
     *
     * @return The local clock time with any computed delta added in.
     */
    public long nanoTime()
    {
        return System.nanoTime() + delta;
    }

    /**
     * Computes the median of a series of values.
     *
     * @param values The values.
     *
     * @return The median.
     */
    public static long median(long[] values)
    {
        // Check if the median is computed from a pair of middle value.
        if ((values.length % 2) == 0)
        {
            int middle = values.length / 2;

            return (values[middle] + values[middle - 1]) / 2;
        }
        // The median is computed from a single middle value.
        else
        {
            return values[values.length / 2];
        }
    }

    /**
     * Computes the mean of a series of values.
     *
     * @param values The values.
     *
     * @return The mean.
     */
    public static long mean(long[] values)
    {
        long total = 0L;

        for (long value : values)
        {
            total += value;
        }

        return total / values.length;
    }

    /**
     * Computes the variance of series of values.
     *
     * @param values The values.
     *
     * @return The variance of the values.
     */
    public static long variance(long[] values)
    {
        long mean = mean(values);

        long totalVariance = 0;

        for (long value : values)
        {
            totalVariance += (value - mean) ^ 2;
        }

        return totalVariance / values.length;
    }

    /**
     * Computes the standard deviation of a series of values.
     *
     * @param values The values.
     *
     * @return The standard deviation.
     */
    public static long standardDeviation(long[] values)
    {
        return Double.valueOf(Math.sqrt(variance(values))).longValue();
    }

    /**
     * For testing purposes. Supply address of reference clock as arg 1.
     *
     * @param args Address of reference clock as arg 1.
     */
    public static void main(String[] args)
    {
        ParsedProperties options =
            new ParsedProperties(CommandLineParser.processCommandLine(args,
                    new CommandLineParser(
                        new String[][]
                        {
                            { "1", "Address of clock reference service.", "address", "true" }
                        }), System.getProperties()));

        String address = options.getProperty("1");

        // Create a clock synchronizer.
        UDPClockSynchronizer clockSyncher = new UDPClockSynchronizer(address);

        // Perform a clock clockSyncher.
        clockSyncher.synch();

        // Print out the clock delta and estimate of the error.
        System.out.println("Delta = " + clockSyncher.getDelta());
        System.out.println("Epsilon = " + clockSyncher.getEpsilon());
    }
}
