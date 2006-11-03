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

import java.util.List;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class WeakReferenceMessageHandle implements AMQMessageHandle
{
    private ContentHeaderBody _contentHeaderBody;

    private List<ContentBody> _contentBodies;

    private boolean _redelivered;

    private final MessageStore _messageStore;

    public WeakReferenceMessageHandle(MessageStore messageStore, ContentHeaderBody contentHeader)
    {
        _messageStore = messageStore;
        _contentHeaderBody = contentHeader;
    }

    public ContentHeaderBody getContentHeaderBody()
    {
        return _contentHeaderBody;
    }

    public void setContentHeaderBody(ContentHeaderBody contentHeaderBody) throws AMQException
    {
        _contentHeaderBody = contentHeaderBody;
    }

    public int getBodyCount()
    {
        return _contentBodies.size();
    }

    public long getBodySize()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public ContentBody getContentBody(int index) throws IllegalArgumentException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void addContentBodyFrame(ContentBody contentBody) throws AMQException
    {
        _contentBodies.add(contentBody);
    }

    public String getExchangeName()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getRoutingKey()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isImmediate()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
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
