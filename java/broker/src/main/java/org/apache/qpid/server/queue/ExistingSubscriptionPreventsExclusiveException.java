package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;

/**
 * ExistingSubscriptionPreventsExclusiveException signals a failure to create an exclusize subscription, as a subscription
 * already exists.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represent failure to create an exclusize subscription, as a subscription already exists.
 * </table>
 *
 * @todo Not an AMQP exception as no status code.
 */
public final class ExistingSubscriptionPreventsExclusiveException extends AMQException
{
    public ExistingSubscriptionPreventsExclusiveException()
    {
        super(null, "", null);
    }
}
