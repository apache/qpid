package org.apache.qpid.util.concurrent;

/**
 * Used to signal that a data element and its producer cannot be requeued or sent an error message when using a
 * {@link BatchSynchQueue} because the producer has already been unblocked by an unblocking take on the queue.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Signal that an unblocking take has already occurred.
 * </table>
 */
public class AlreadyUnblockedException extends RuntimeException
{ }
