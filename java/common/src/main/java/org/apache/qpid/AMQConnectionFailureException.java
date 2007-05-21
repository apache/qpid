package org.apache.qpid;

/**
 * AMQConnectionFailureException indicates that a connection to a broker could not be formed.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents failure to connect to a broker.
 * </table>
 *
 * @todo Not an AMQP exception as no status code.
 */
public class AMQConnectionFailureException extends AMQException
{
    public AMQConnectionFailureException(String message, Throwable cause)
    {
        super(null, message, cause);
    }
}
