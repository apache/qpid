package org.apache.qpid.util.concurrent;

import java.util.Queue;

/**
 * SynchBuffer completes the {@link BatchSynchQueueBase} abstract class by providing an implementation of the underlying
 * queue as an array. This uses FIFO ordering for the queue but restricts the maximum size of the queue to a fixed
 * amount. It also has the advantage that, as the buffer does not grow and shrink dynamically, memory for the buffer
 * is allocated up front and does not create garbage during the operation of the queue.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide array based FIFO queue to create a batch synched queue around.
 * </table>
 *
 * @todo Write an array based buffer implementation that implements Queue.
 */
public class SynchBuffer<E> extends BatchSynchQueueBase<E>
{
    /**
     * Returns an empty queue, implemented as an array.
     *
     * @return An empty queue, implemented as an array.
     */
    protected <T> Queue<T> createQueue()
    {
        throw new RuntimeException("Not implemented.");
    }
}
