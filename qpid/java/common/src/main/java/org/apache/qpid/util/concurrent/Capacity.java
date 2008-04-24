package org.apache.qpid.util.concurrent;

/**
 * An interface exposed by data structures that have a maximum capacity.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Report the maximum capacity.
 * </table>
 */
public interface Capacity
{
    public int getCapacity();
}
