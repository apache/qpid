/**
 * User: Robert Greig
 * Date: 01-Nov-2006
 ******************************************************************************
 * (c) Copyright JP Morgan Chase Ltd 2006. All rights reserved. No part of
 * this program may be photocopied reproduced or translated to another
 * program language without prior written consent of JP Morgan Chase Ltd
 ******************************************************************************/
package org.apache.qpid.server.txn;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.protocol.AMQProtocolSession;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public interface TransactionalContext
{
    void beginTranIfNecessary() throws AMQException;

    void commit() throws AMQException;

    void rollback() throws AMQException;

    void deliver(AMQMessage message, AMQQueue queue) throws AMQException;

    void acknowledgeMessage(long deliveryTag, long lastDeliveryTag, boolean multiple,
                            UnacknowledgedMessageMap unacknowledgedMessageMap) throws AMQException;

    void messageFullyReceived(boolean persistent) throws AMQException;

    void messageProcessed(AMQProtocolSession protocolSession) throws AMQException;
}
