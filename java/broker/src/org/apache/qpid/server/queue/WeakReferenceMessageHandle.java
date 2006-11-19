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
import java.lang.ref.WeakReference;

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

    private final long _messageId;

    public WeakReferenceMessageHandle(MessageStore messageStore, long messageId)
    {
        _messageStore = messageStore;
        _messageId = messageId;
    }

    public ContentHeaderBody getContentHeaderBody() throws AMQException
    {
        ContentHeaderBody chb = _contentHeaderBody.get();
        if (chb == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(_messageId);
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

    public long getBodySize() throws AMQException
    {
        return getContentHeaderBody().bodySize;
    }

    public ContentBody getContentBody(int index) throws AMQException, IllegalArgumentException
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
            cb = _messageStore.getContentBodyChunk(_messageId, index);
            _contentBodies.set(index, new WeakReference<ContentBody>(cb));
        }
        return cb;
    }

    public void addContentBodyFrame(ContentBody contentBody) throws AMQException
    {
        _contentBodies.add(new WeakReference<ContentBody>(contentBody));
        _messageStore.storeContentBodyChunk(_messageId, _contentBodies.size() - 1, contentBody);
    }

    public BasicPublishBody getPublishBody() throws AMQException
    {
        BasicPublishBody bpb = _publishBody.get();
        if (bpb == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(_messageId);
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

    public boolean isPersistent() throws AMQException
    {
        //todo remove literal values to a constant file such as AMQConstants in common
        ContentHeaderBody chb = getContentHeaderBody();
        return chb.properties instanceof BasicContentHeaderProperties &&
               ((BasicContentHeaderProperties) chb.properties).getDeliveryMode() == 2;
    }

    /**
     * This is called when all the content has been received.
     * @param publishBody
     * @param contentHeaderBody
     * @throws AMQException
     */
    public void setPublishAndContentHeaderBody(BasicPublishBody publishBody, ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        _messageStore.storeMessageMetaData(_messageId, new MessageMetaData(publishBody, contentHeaderBody,
                                                                           _contentBodies.size()));
        _publishBody = new WeakReference<BasicPublishBody>(publishBody);
        _contentHeaderBody = new WeakReference<ContentHeaderBody>(contentHeaderBody);
    }

    public void removeMessage() throws AMQException
    {
        _messageStore.removeMessage(_messageId);
    }
}
