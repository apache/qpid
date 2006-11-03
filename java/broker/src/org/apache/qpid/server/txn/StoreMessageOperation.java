/**
 * User: Robert Greig
 * Date: 01-Nov-2006
 ******************************************************************************
 * (c) Copyright JP Morgan Chase Ltd 2006. All rights reserved. No part of
 * this program may be photocopied reproduced or translated to another
 * program language without prior written consent of JP Morgan Chase Ltd
 ******************************************************************************/
package org.apache.qpid.server.txn;

import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.AMQException;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class StoreMessageOperation implements TxnOp
{
    //just use this to do a store of the message during the
    //prepare phase. Any enqueueing etc is done by TxnOps enlisted
    //by the queues themselves.
    private final AMQMessage _msg;

    public StoreMessageOperation(AMQMessage msg)
    {
        _msg = msg;
    }

    public void prepare() throws AMQException
    {
        _msg.storeMessage();
        // the router's reference can now be released
        _msg.decrementReference();
    }

    public void undoPrepare()
    {
    }

    public void commit()
    {
    }

    public void rollback()
    {
    }
}
