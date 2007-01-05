/**
 * User: Robert Greig
 * Date: 01-Nov-2006
 ******************************************************************************
 * (c) Copyright JP Morgan Chase Ltd 2006. All rights reserved. No part of
 * this program may be photocopied reproduced or translated to another
 * program language without prior written consent of JP Morgan Chase Ltd
 ******************************************************************************/
package org.apache.qpid.server.txn;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.StoreContext;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class DeliverMessageOperation implements TxnOp
{
    private static final Logger _logger = Logger.getLogger(DeliverMessageOperation.class);

    private final AMQMessage _msg;

    private final AMQQueue _queue;

    public DeliverMessageOperation(AMQMessage msg, AMQQueue queue)
    {
        _msg = msg;
        _queue = queue;
        _msg.incrementReference();
    }

    public void prepare(StoreContext context) throws AMQException
    {
    }

    public void undoPrepare()
    {
    }

    public void commit(StoreContext context)
    {
        //do the memeory part of the record()
        _msg.incrementReference();
        //then process the message
        try
        {
            _queue.process(context, _msg);
        }
        catch (AMQException e)
        {
            //TODO: is there anything else we can do here? I think not...
            _logger.error("Error during commit of a queue delivery: " + e, e);
        }
    }

    public void rollback(StoreContext storeContext)
    {
    }
}
