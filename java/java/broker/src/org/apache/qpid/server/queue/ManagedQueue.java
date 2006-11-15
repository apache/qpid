/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.queue;

import org.apache.qpid.server.management.MBeanAttribute;
import org.apache.qpid.server.management.MBeanOperation;
import org.apache.qpid.server.management.MBeanOperationParameter;

import javax.management.JMException;
import javax.management.MBeanOperationInfo;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.CompositeData;
import java.io.IOException;

/**
 * The management interface exposed to allow management of a queue.
 * @author  Robert J. Greig
 * @author  Bhupendra Bhardwaj
 * @version 0.1
 */
public interface ManagedQueue
{
    static final String TYPE = "Queue";

    /**
     * Returns the Name of the ManagedQueue.
     * @return the name of the managedQueue.
     * @throws IOException
     */
    @MBeanAttribute(name="Name", description = "Name of the " + TYPE)
    String getName() throws IOException;

    /**
     * Tells whether this ManagedQueue is durable or not.
     * @return true if this ManagedQueue is a durable queue.
     * @throws IOException
     */
    @MBeanAttribute(name="Durable", description = "true if the AMQQueue is durable")
    boolean isDurable() throws IOException;

    /**
     * Tells the Owner of the ManagedQueue.
     * @return the owner's name.
     * @throws IOException
     */
    @MBeanAttribute(name="Owner", description = "Owner")
    String getOwner() throws IOException;

    /**
     * Tells if the ManagedQueue is set to AutoDelete.
     * @return  true if the ManagedQueue is set to AutoDelete.
     * @throws IOException
     */
    @MBeanAttribute(name="AutoDelete", description = "true if the AMQQueue is AutoDelete")
    boolean isAutoDelete() throws IOException;

    /**
     * Total number of messages on the queue, which are yet to be delivered to the consumer(s).
     * @return number of undelivered message in the Queue.
     * @throws IOException
     */
    @MBeanAttribute(name="MessageCount",
                         description = "Total number of undelivered messages on the queue")
    Integer getMessageCount() throws IOException;

    /**
     * Returns the maximum size of a message (in kbytes) allowed to be accepted by the
     * ManagedQueue. This is useful in setting notifications or taking
     * appropriate action, if the size of the message received is more than
     * the allowed size.
     * @return the maximum size of a message allowed to be aceepted by the
     *         ManagedQueue.
     * @throws IOException
     */
    Long getMaximumMessageSize() throws IOException;

    /**
     * Sets the maximum size of the message (in kbytes) that is allowed to be
     * accepted by the Queue.
     * @param size  maximum size of message.
     * @throws IOException
     */
    @MBeanAttribute(name="MaximumMessageSize",
                         description="Maximum size(KB) of a message allowed for this Queue")
    void setMaximumMessageSize(Long size) throws IOException;

    /**
     * Returns the total number of subscribers to the queue.
     * @return the number of subscribers.
     * @throws IOException
     */
    @MBeanAttribute(name="ConsumerCount", description="The total number of subscribers to the queue")
    Integer getConsumerCount() throws IOException;

    /**
     *  Returns the total number of active subscribers to the queue.
     * @return the number of active subscribers
     * @throws IOException
     */
    @MBeanAttribute(name="ActiveConsumerCount", description="The total number of active subscribers to the queue")
    Integer getActiveConsumerCount() throws IOException;

    /**
     * Tells the total number of messages receieved by the queue since startup.
     * @return total number of messages received.
     * @throws IOException
     */
    @MBeanAttribute(name="ReceivedMessageCount",
                         description="The total number of messages receieved by the queue since startup")
    Long getReceivedMessageCount() throws IOException;

    /**
     * Tells the maximum number of messages that can be stored in the queue.
     * This is useful in setting the notifications or taking required
     * action is the number of message increase this limit.
     * @return maximum muber of message allowed to be stored in the queue.
     * @throws IOException
     */
    Integer getMaximumMessageCount() throws IOException;

    /**
     * Sets the maximum number of messages allowed to be stored in the queue.
     * @param value  the maximum number of messages allowed to be stored in the queue.
     * @throws IOException
     */
    @MBeanAttribute(name="MaximumMessageCount",
                         description="The maximum number of messages allowed to be stored in the queue")
    void setMaximumMessageCount(Integer value) throws IOException;

    /**
     * Size of messages in the queue
     * @return
     * @throws IOException
     */
    @MBeanAttribute(name="QueueSize", description="Size of messages(KB) in the queue")
    Long getQueueSize() throws IOException;

    /**
     * Tells the maximum size of all the messages combined together,
     * that can be stored in the queue. This is useful for setting notifications
     * or taking required action if the size of messages stored in the queue
     * increases over this limit.
     * @return maximum size of the all the messages allowed for the queue.
     * @throws IOException
     */
    Long getQueueDepth() throws IOException;

    /**
     * Sets the maximum size of all the messages together, that can be stored
     * in the queue.
     * @param value
     * @throws IOException
     */
    @MBeanAttribute(name="QueueDepth",
                         description="The size(KB) of all the messages together, that can be stored in the queue")
    void setQueueDepth(Long value) throws IOException;



    //********** Operations *****************//


    /**
     * Returns a subset of all the messages stored in the queue. The messages
     * are returned based on the given index numbers.
     * @param fromIndex
     * @param toIndex
     * @return
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="viewMessages",
                         description="shows messages in this queue with given indexes. eg. from index 1 - 100")
    TabularData viewMessages(@MBeanOperationParameter(name="from index", description="from index")int fromIndex,
                             @MBeanOperationParameter(name="to index", description="to index")int toIndex)
        throws IOException, JMException;

    /**
     * Deletes the first message from top.
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="deleteMessageFromTop",
                         description="Deletes the first message from top",
                         impact= MBeanOperationInfo.ACTION)
    void deleteMessageFromTop() throws IOException, JMException;

    /**
     * Clears the queue by deleting all the undelivered messages from the queue.
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="clearQueue",
                         description="Clears the queue by deleting all the undelivered messages from the queue",
                         impact= MBeanOperationInfo.ACTION)
    void clearQueue() throws IOException, JMException;

    @MBeanOperation(name="viewMessageContent",
                         description="Returns the message content along with MimeType and Encoding")
    CompositeData viewMessageContent(@MBeanOperationParameter(name="Message Id", description="Message Id")long messageId) 
        throws IOException, JMException;
}
