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
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;

/**
 * A transactional operation to store messages in an underlying persistent store. When this operation
 * commits it will do everything to ensure that all messages are safely committed to persistent
 * storage.
 */
public class StoreMessageOperation implements TxnOp
{
    private final MessageStore _messsageStore;

    public StoreMessageOperation(MessageStore messageStore)
    {
        _messsageStore = messageStore;
    }

    public void prepare(StoreContext context) throws AMQException
    {
    }

    public void undoPrepare()
    {
    }

    public void commit(StoreContext context) throws AMQException
    {
        _messsageStore.commitTran(context);
    }

    public void rollback(StoreContext context) throws AMQException
    {
        _messsageStore.abortTran(context);
    }
}
