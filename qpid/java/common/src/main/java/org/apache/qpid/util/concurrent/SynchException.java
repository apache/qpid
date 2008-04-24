package org.apache.qpid.util.concurrent;

/**
 * SynchException is used to encapsulate exceptions with the data elements that caused them in order to send exceptions
 * back from the consumers of a {@link BatchSynchQueue} to producers. The underlying exception should be retrieved from
 * the {@link #getCause} method.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Encapsulate a data element and exception.
 * </table>
 */
public class SynchException extends Exception
{
    /** Holds the data element that is in error. */
    Object element;

    /**
     * Creates a new BaseApplicationException object.
     *
     * @param message The exception message.
     * @param cause   The underlying throwable cause. This may be null.
     */
    public SynchException(String message, Throwable cause, Object element)
    {
        super(message, cause);

        // Keep the data element that was in error.
        this.element = element;
    }
}
