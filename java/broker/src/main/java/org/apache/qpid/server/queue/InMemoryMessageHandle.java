/**
 * User: Robert Greig
 * Date: 21-Nov-2006
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

import java.util.LinkedList;
import java.util.List;

/**
 */
public class InMemoryMessageHandle implements AMQMessageHandle
{

    private ContentHeaderBody _contentHeaderBody;

    private BasicPublishBody _publishBody;

    private List<ContentBody> _contentBodies = new LinkedList<ContentBody>();

    private boolean _redelivered;

    public InMemoryMessageHandle()
    {
    }

    public ContentHeaderBody getContentHeaderBody(long messageId) throws AMQException
    {
        return _contentHeaderBody;
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
        return _contentBodies.get(index);
    }

    public void addContentBodyFrame(long messageId, ContentBody contentBody) throws AMQException
    {
        _contentBodies.add(contentBody);
    }

    public BasicPublishBody getPublishBody(long messageId) throws AMQException
    {
        return _publishBody;
    }

    public boolean isRedelivered()
    {
        return _redelivered;
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
        _publishBody = publishBody;
        _contentHeaderBody = contentHeaderBody;
    }

    public void removeMessage(long messageId) throws AMQException
    {
    }

    public void enqueue(long messageId, AMQQueue queue) throws AMQException
    {
    }

    public void dequeue(long messageId, AMQQueue queue) throws AMQException
    {
    }
}
