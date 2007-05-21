package org.apache.qpid;

/**
 * AMQUnknownExchangeType represents coding error where unknown exchange type requested from exchange factory.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents unknown exchange type request.
 * <tr><td>
 *
 * @todo Not an AMQP exception as no status code.
 *
 * @todo Represent coding error, where unknown exchange type is requested by passing a string parameter. Use a type safe
 *       enum for the exchange type, or replace with IllegalArgumentException. Should be runtime.
 */
public class AMQUnknownExchangeType extends AMQException
{
    public AMQUnknownExchangeType(String message)
    {
        super(message);
    }
}
