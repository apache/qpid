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
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.store.MessageStore;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class WeakReferenceMessageHandle implements AMQMessageHandle
{
    private WeakReference<ContentHeaderBody> _contentHeaderBody;

    private WeakReference<BasicPublishBody> _publishBody;

    private List<WeakReference<ContentBody>> _contentBodies = new LinkedList<WeakReference<ContentBody>>();

    private boolean _redelivered;

    private final MessageStore _messageStore;

    public WeakReferenceMessageHandle(MessageStore messageStore)
    {
        _messageStore = messageStore;
    }

    public ContentHeaderBody getContentHeaderBody(long messageId) throws AMQException
    {
        ContentHeaderBody chb = (_contentHeaderBody != null?_contentHeaderBody.get():null);
        if (chb == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(messageId);
            chb = mmd.getContentHeaderBody();
            _contentHeaderBody = new WeakReference<ContentHeaderBody>(chb);
            _publishBody = new WeakReference<BasicPublishBody>(mmd.getPublishBody());
        }
        return chb;
    }

    public int getBodyCount()
    {
        return _contentBodies.size();
    }

    public long getBodySize(long messageId) throws AMQException
    {
        return getContentHeaderBody(messageId).bodySize;
    }

    public ContentBody getContentBody(long messageId, int index) throws AMQException, IllegalArgumentException
    {
        if (index > _contentBodies.size() - 1)
        {
            throw new IllegalArgumentException("Index " + index + " out of valid range 0 to " +
                                               (_contentBodies.size() - 1));
        }
        WeakReference<ContentBody> wr = _contentBodies.get(index);
        ContentBody cb = wr.get();
        if (cb == null)
        {
            cb = _messageStore.getContentBodyChunk(messageId, index);
            _contentBodies.set(index, new WeakReference<ContentBody>(cb));
        }
        return cb;
    }

    public void addContentBodyFrame(long messageId, ContentBody contentBody) throws AMQException
    {
        _contentBodies.add(new WeakReference<ContentBody>(contentBody));
        _messageStore.storeContentBodyChunk(messageId, _contentBodies.size() - 1, contentBody);
    }

    public BasicPublishBody getPublishBody(long messageId) throws AMQException
    {
        BasicPublishBody bpb = (_publishBody != null?_publishBody.get():null);
        if (bpb == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(messageId);
            bpb = mmd.getPublishBody();
            _publishBody = new WeakReference<BasicPublishBody>(bpb);
            _contentHeaderBody = new WeakReference<ContentHeaderBody>(mmd.getContentHeaderBody());
        }
        return bpb;
    }

    public boolean isRedelivered()
    {
        return _redelivered;
    }

    public void setRedelivered(boolean redelivered)
    {
        _redelivered = redelivered;
    }

    public boolean isPersistent(long messageId) throws AMQException
    {
        //todo remove literal values to a constant file such as AMQConstants in common
        ContentHeaderBody chb = getContentHeaderBody(messageId);
        return chb.properties instanceof BasicContentHeaderProperties &&
               ((BasicContentHeaderProperties) chb.properties).getDeliveryMode() == 2;
    }

    /**
     * This is called when all the content has been received.
     * @param publishBody
     * @param contentHeaderBody
     * @throws AMQException
     */
    public void setPublishAndContentHeaderBody(long messageId, BasicPublishBody publishBody,
                                               ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        _messageStore.storeMessageMetaData(messageId, new MessageMetaData(publishBody, contentHeaderBody,
                                                                           _contentBodies.size()));
        _publishBody = new WeakReference<BasicPublishBody>(publishBody);
        _contentHeaderBody = new WeakReference<ContentHeaderBody>(contentHeaderBody);
    }

    public void removeMessage(long messageId) throws AMQException
    {
        _messageStore.removeMessage(messageId);
    }

    public void enqueue(long messageId, AMQQueue queue) throws AMQException
    {
        _messageStore.enqueueMessage(queue.getName(), messageId);
    }

    public void dequeue(long messageId, AMQQueue queue) throws AMQException
    {
        _messageStore.dequeueMessage(queue.getName(), messageId);
    }
}
