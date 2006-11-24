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
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;

/**
 * A pluggable way of getting message data. Implementations can provide intelligent caching for example or
 * even no caching at all to minimise the broker memory footprint.
 *
 * The method all take a messageId to avoid having to store it in the instance - the AMQMessage container
 * must already keen the messageId so it is pointless storing it twice. 
 */
public interface AMQMessageHandle
{
    ContentHeaderBody getContentHeaderBody(long messageId) throws AMQException;

    /**
     * @return the number of body frames associated with this message
     */
    int getBodyCount();

    /**
     * @return the size of the body
     */
    long getBodySize(long messageId) throws AMQException;

    /**
     * Get a particular content body
     * @param index the index of the body to retrieve, must be between 0 and getBodyCount() - 1
     * @return a content body
     * @throws IllegalArgumentException if the index is invalid
     */
    ContentBody getContentBody(long messageId, int index) throws IllegalArgumentException, AMQException;

    void addContentBodyFrame(long messageId, ContentBody contentBody) throws AMQException;

    BasicPublishBody getPublishBody(long messageId) throws AMQException;

    boolean isRedelivered();

    boolean isPersistent(long messageId) throws AMQException;

    void setPublishAndContentHeaderBody(long messageId, BasicPublishBody publishBody,
                                        ContentHeaderBody contentHeaderBody)
            throws AMQException;

    void removeMessage(long messageId) throws AMQException;

    void enqueue(long messageId, AMQQueue queue) throws AMQException;

    void dequeue(long messageId, AMQQueue queue) throws AMQException;
}