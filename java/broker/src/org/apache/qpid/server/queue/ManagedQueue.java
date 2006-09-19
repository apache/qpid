/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import javax.management.openmbean.TabularData;
import javax.management.JMException;
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
    String getName() throws IOException;

    /**
     * Tells whether this ManagedQueue is durable or not.
     * @return true if this ManagedQueue is a durable queue.
     * @throws IOException
     */
    boolean isDurable() throws IOException;

    /**
     * Tells the Owner of the ManagedQueue.
     * @return the owner's name.
     * @throws IOException
     */
    String getOwner() throws IOException;

    /**
     * Tells if the ManagedQueue is set to AutoDelete.
     * @return  true if the ManagedQueue is set to AutoDelete.
     * @throws IOException
     */
    boolean isAutoDelete() throws IOException;

    /**
     * Gets the total number of messages on the queue, which are yet to be
     * delivered to the consumer(s).
     * @return number of undelivered message in the Queue.
     * @throws IOException
     */
    int getMessageCount() throws IOException;

    /**
     * Returns the maximum size of a message (in bytes) allowed to be accepted by the
     * ManagedQueue. This is useful in setting notifications or taking
     * appropriate action, if the size of the message received is more than
     * the allowed size.
     * @return the maximum size of a message allowed to be aceepted by the
     *         ManagedQueue.
     * @throws IOException
     */
    long getMaximumMessageSize() throws IOException;

    /**
     * Sets the maximum size of the message (in bytes) that is allowed to be
     * accepted by the Queue.
     * @param bytes  maximum size of message.
     * @throws IOException
     */
    void setMaximumMessageSize(long bytes) throws IOException;

    /**
     * Returns the total number of subscribers to the queue.
     * @return the number of subscribers.
     * @throws IOException
     */
    int getConsumerCount() throws IOException;

    /**
     *  Returns the total number of active subscribers to the queue.
     * @return the number of active subscribers
     * @throws IOException
     */
    int getActiveConsumerCount() throws IOException;

    /**
     * Tells the total number of messages receieved by the queue since startup.
     * @return total number of messages received.
     * @throws IOException
     */
    long getReceivedMessageCount() throws IOException;

    /**
     * Tells the maximum number of messages that can be stored in the queue.
     * This is useful in setting the notifications or taking required
     * action is the number of message increase this limit.
     * @return maximum muber of message allowed to be stored in the queue.
     * @throws IOException
     */
    long getMaximumMessageCount() throws IOException;

    /**
     * Sets the maximum number of messages allowed to be stored in the queue.
     * @param value  the maximum number of messages allowed to be stored in the queue.
     * @throws IOException
     */
    void setMaximumMessageCount(long value) throws IOException;

    /**
     * Tells the maximum size of all the messages combined together,
     * that can be stored in the queue. This is useful for setting notifications
     * or taking required action if the size of messages stored in the queue
     * increases over this limit.
     * @return maximum size of the all the messages allowed for the queue.
     * @throws IOException
     */
    long getQueueDepth() throws IOException;

    /**
     * Sets the maximum size of all the messages together, that can be stored
     * in the queue.
     * @param value
     * @throws IOException
     */
    void setQueueDepth(long value) throws IOException;



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
    TabularData viewMessages(int fromIndex, int toIndex)
        throws IOException, JMException;

    /**
     * Deletes the first message from top.
     * @throws IOException
     * @throws JMException
     */
    void deleteMessageFromTop()
         throws IOException, JMException;

    /**
     * Clears the queue by deleting all the messages from the queue.
     * @throws IOException
     * @throws JMException
     */
    void clearQueue()
        throws IOException, JMException;

}
