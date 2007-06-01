package org.apache.qpid.client.failover;

import org.apache.qpid.client.AMQConnection;

/**
 * FailoverNoopSupport is a {@link FailoverSupport} implementation that does not really provide any failover support
 * at all. It wraps a {@link FailoverProtectedOperation} but should that operation throw {@link FailoverException} this
 * support class simply re-raises that exception as an IllegalStateException. This support wrapper should only be
 * used where the caller can be certain that the failover protected operation cannot acutally throw a failover exception,
 * for example, because the caller already holds locks preventing that condition from arising.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Perform a fail-over protected operation with no handling of fail-over conditions.
 * </table>
 */
public class FailoverNoopSupport<T, E extends Exception> implements FailoverSupport<T, E>
{
    /** The protected operation that is to be retried in the event of fail-over. */
    FailoverProtectedOperation<T, E> operation;

    /** The connection on which the fail-over protected operation is to be performed. */
    AMQConnection connection;

    /**
     * Creates an automatic retrying fail-over handler for the specified operation.
     *
     * @param operation The fail-over protected operation to wrap in this handler.
     */
    public FailoverNoopSupport(FailoverProtectedOperation<T, E> operation, AMQConnection con)
    {
        this.operation = operation;
        this.connection = con;
    }

    /**
     * Delegates to another continuation which is to be provided with fail-over handling.
     *
     * @return The return value from the delegated to continuation.
     * @throws E Any exception that the delegated to continuation may raise.
     */
    public T execute() throws E
    {
        try
        {
            return operation.execute();
        }
        catch (FailoverException e)
        {
            throw new IllegalStateException("Fail-over interupted no-op failover support. "
                + "No-op support should only be used where the caller is certaing fail-over cannot occur.", e);
        }
    }
}
