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
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.BasicPublishBody;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public interface AMQMessageHandle
{
    ContentHeaderBody getContentHeaderBody();

    void setContentHeaderBody(ContentHeaderBody contentHeaderBody) throws AMQException;

    /**
     * @return the number of body frames associated with this message
     */
    int getBodyCount();

    /**
     * @return the size of the body
     */
    long getBodySize();

    /**
     * Get a particular content body
     * @param index the index of the body to retrieve, must be between 0 and getBodyCount() - 1
     * @return a content body
     * @throws IllegalArgumentException if the index is invalid
     */
    ContentBody getContentBody(int index) throws IllegalArgumentException;

    void addContentBodyFrame(ContentBody contentBody) throws AMQException;

    BasicPublishBody getPublishBody() throws AMQException;

    boolean isRedelivered();

    boolean isPersistent() throws AMQException;

    void setPublishBody(BasicPublishBody publishBody)
            ;
}