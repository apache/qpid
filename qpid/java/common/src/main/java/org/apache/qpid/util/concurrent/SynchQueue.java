package org.apache.qpid.util.concurrent;

import java.util.LinkedList;
import java.util.Queue;

/**
 * SynchQueue completes the {@link BatchSynchQueueBase} abstract class by providing an implementation of the underlying
 * queue as a linked list. This uses FIFO ordering for the queue and allows the queue to grow to accomodate more
 * elements as needed.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide linked list FIFO queue to create a batch synched queue around.
 * </table>
 */
public class SynchQueue<E> extends BatchSynchQueueBase<E>
{
    /**
     * Returns an empty queue, implemented as a linked list.
     *
     * @return An empty queue, implemented as a linked list.
     */
    protected <T> Queue<T> createQueue()
    {
        return new LinkedList<T>();
    }
}
