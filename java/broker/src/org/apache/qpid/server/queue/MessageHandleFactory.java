/**
 * User: Robert Greig
 * Date: 31-Oct-2006
 ******************************************************************************
 * (c) Copyright JP Morgan Chase Ltd 2006. All rights reserved. No part of
 * this program may be photocopied reproduced or translated to another
 * program language without prior written consent of JP Morgan Chase Ltd
 ******************************************************************************/
package org.apache.qpid.server.queue;

import org.apache.qpid.server.store.MessageStore;

/**
 * Constructs a message handle based on the publish body, the content header and the queue to which the message
 * has been routed.
 *
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class MessageHandleFactory
{

    public AMQMessageHandle createMessageHandle(long messageId, MessageStore store, boolean persistent)
    {
        // just hardcoded for now
        return new WeakReferenceMessageHandle(store, messageId);
    }
}
