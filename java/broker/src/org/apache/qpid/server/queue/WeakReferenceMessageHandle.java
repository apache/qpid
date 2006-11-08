/**
 * User: Robert Greig
 * Date: 23-Oct-2006
 ******************************************************************************
 * (c) Copyright JP Morgan Chase Ltd 2006. All rights reserved. No part of
 * this program may be photocopied reproduced or translated to another
 * program language without prior written consent of JP Morgan Chase Ltd
 ******************************************************************************/
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.BasicPublishBody;

import java.util.List;
import java.util.LinkedList;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class WeakReferenceMessageHandle implements AMQMessageHandle
{
    private ContentHeaderBody _contentHeaderBody;

    private BasicPublishBody _publishBody;

    private List<ContentBody> _contentBodies = new LinkedList<ContentBody>();

    private boolean _redelivered;

    private final MessageStore _messageStore;

    private final long _messageId;

    public WeakReferenceMessageHandle(MessageStore messageStore, long messageId)
    {
        _messageStore = messageStore;
        _messageId = messageId;
    }

    public ContentHeaderBody getContentHeaderBody()
    {
        return _contentHeaderBody;
    }

    public void setContentHeaderBody(ContentHeaderBody contentHeaderBody) throws AMQException
    {
        _contentHeaderBody = contentHeaderBody;
        _messageStore.storePublishBody(_messageId, _publishBody);
        _messageStore.storeContentHeader(_messageId, contentHeaderBody);
    }

    public int getBodyCount()
    {
        return _contentBodies.size();
    }

    public long getBodySize()
    {
        return _contentHeaderBody.bodySize;
    }

    public ContentBody getContentBody(int index) throws IllegalArgumentException
    {
        return _contentBodies.get(index);
    }

    public void addContentBodyFrame(ContentBody contentBody) throws AMQException
    {
        _contentBodies.add(contentBody);
        _messageStore.storeContentBodyChunk(_messageId, _contentBodies.size() - 1, contentBody);
    }

    public void setPublishBody(BasicPublishBody publishBody)
    {
        _publishBody = publishBody;
    }

    public BasicPublishBody getPublishBody() throws AMQException
    {
        return _publishBody;
    }

    public boolean isRedelivered()
    {
        return _redelivered;
    }

    public boolean isPersistent() throws AMQException
    {
        if (_contentHeaderBody == null)
        {
            throw new AMQException("Cannot determine delivery mode of message. Content header not found.");
        }

        //todo remove literal values to a constant file such as AMQConstants in common
        return _contentHeaderBody.properties instanceof BasicContentHeaderProperties
                && ((BasicContentHeaderProperties) _contentHeaderBody.properties).getDeliveryMode() == 2;
    }
}
