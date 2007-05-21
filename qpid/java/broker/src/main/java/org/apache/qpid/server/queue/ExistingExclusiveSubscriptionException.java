package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;

/**
 * ExistingExclusiveSubscriptionException signals a failure to create a subscription, because an exclusive subscription
 * already exists.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represent failure to create a subscription, because an exclusive subscription already exists.
 * </table>
 *
 * @todo Not an AMQP exception as no status code.
 */
public final class ExistingExclusiveSubscriptionException extends AMQException
{
    public ExistingExclusiveSubscriptionException()
    {
        super(null, "", null);
    }
}
