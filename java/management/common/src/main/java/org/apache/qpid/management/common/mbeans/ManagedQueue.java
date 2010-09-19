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
package org.apache.qpid.management.common.mbeans;

import java.io.IOException;
import java.util.*;

import javax.management.JMException;
import javax.management.MBeanOperationInfo;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.qpid.management.common.mbeans.annotations.MBeanAttribute;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperation;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;

/**
 * The management interface exposed to allow management of a queue.
 * @author  Robert J. Greig
 * @author  Bhupendra Bhardwaj
 * @version 0.1
 */
public interface ManagedQueue
{
    static final String TYPE = "Queue";

    //TabularType and contained CompositeType key/description information for message list
    //For compatibility reasons, DONT MODIFY the existing key values if expanding the set. 
    //"Queue Position" added in Qpid JMX API 1.3
    String MSG_AMQ_ID = "AMQ MessageId";
    String MSG_HEADER = "Header";
    String MSG_SIZE = "Size(bytes)";
    String MSG_REDELIVERED = "Redelivered";
    String MSG_QUEUE_POS = "Queue Position";
    List<String> VIEW_MSGS_COMPOSITE_ITEM_NAMES_DESC = Collections.unmodifiableList(Arrays.asList(MSG_AMQ_ID, MSG_HEADER, MSG_SIZE, MSG_REDELIVERED, MSG_QUEUE_POS));
    List<String> VIEW_MSGS_TABULAR_UNIQUE_INDEX = Collections.unmodifiableList(Arrays.asList(MSG_QUEUE_POS));
  
    //CompositeType key/description information for message content
    //For compatibility reasons, DONT MODIFY the existing key values if expanding the set.
    String MIME = "MimeType";
    String ENCODING = "Encoding";
    String CONTENT = "Content";
    List<String> VIEW_MSG_CONTENT_COMPOSITE_ITEM_NAMES_DESC = Collections.unmodifiableList(Arrays.asList(MSG_AMQ_ID, MIME, ENCODING, CONTENT));
    
    //Individual attribute name constants
    static final String ATTR_NAME = "Name";
    static final String ATTR_OWNER = "Owner";
    static final String ATTR_MAX_MSG_AGE = "MaximumMessageAge";
    static final String ATTR_MAX_MSG_COUNT = "MaximumMessageCount";
    static final String ATTR_MAX_QUEUE_DEPTH = "MaximumQueueDepth";
    static final String ATTR_MAX_MSG_SIZE = "MaximumMessageSize";
    static final String ATTR_DURABLE = "Durable";
    static final String ATTR_AUTODELETE = "AutoDelete";
    static final String ATTR_CONSUMER_COUNT = "ConsumerCount";
    static final String ATTR_ACTIVE_CONSUMER_COUNT = "ActiveConsumerCount";
    static final String ATTR_MSG_COUNT = "MessageCount";
    static final String ATTR_QUEUE_DEPTH = "QueueDepth";
    static final String ATTR_RCVD_MSG_COUNT = "ReceivedMessageCount";
    static final String ATTR_CAPACITY = "Capacity";
    static final String ATTR_FLOW_OVERFULL = "FlowOverfull";
    static final String ATTR_FLOW_RESUME_CAPACITY = "FlowResumeCapacity";
    static final String ATTR_EXCLUSIVE = "Exclusive";
    
    //All attribute names constant
    static final List<String> QUEUE_ATTRIBUTES
            = Collections.unmodifiableList(
                new ArrayList<String>(
                        new HashSet<String>(
                                Arrays.asList(
                                    ATTR_NAME,
                                    ATTR_OWNER,
                                    ATTR_MAX_MSG_AGE,
                                    ATTR_MAX_MSG_COUNT,
                                    ATTR_MAX_QUEUE_DEPTH,
                                    ATTR_MAX_MSG_SIZE,
                                    ATTR_DURABLE,
                                    ATTR_AUTODELETE,
                                    ATTR_CONSUMER_COUNT,
                                    ATTR_ACTIVE_CONSUMER_COUNT,
                                    ATTR_MSG_COUNT,
                                    ATTR_QUEUE_DEPTH,
                                    ATTR_RCVD_MSG_COUNT,
                                    ATTR_CAPACITY,
                                    ATTR_FLOW_OVERFULL,
                                    ATTR_FLOW_RESUME_CAPACITY,
                                    ATTR_EXCLUSIVE))));

    /**
     * Returns the Name of the ManagedQueue.
     * @return the name of the managedQueue.
     * @throws IOException
     */
    @MBeanAttribute(name="Name", description = TYPE + " Name")
    String getName() throws IOException;

    /**
     * Total number of messages on the queue, which are yet to be delivered to the consumer(s).
     * @return number of undelivered message in the Queue.
     * @throws IOException
     */
    @MBeanAttribute(name="MessageCount", description = "Total number of undelivered messages on the queue")
    Integer getMessageCount() throws IOException;

    /**
     * Tells the total number of messages receieved by the queue since startup.
     * @return total number of messages received.
     * @throws IOException
     */
    @MBeanAttribute(name="ReceivedMessageCount", description="The total number of messages receieved by the queue since startup")
    Long getReceivedMessageCount() throws IOException;

    /**
     * Size of messages in the queue
     * 
     * Since Qpid JMX API 1.2 this operation returns in units of bytes. Prior to this, the result was in units of kilobytes.
     * @return
     * @throws IOException
     */
    @MBeanAttribute(name="QueueDepth", description="The total size(Bytes) of messages in the queue")
    Long getQueueDepth() throws IOException, JMException;

    /**
     *  Returns the total number of active subscribers to the queue.
     * @return the number of active subscribers
     * @throws IOException
     */
    @MBeanAttribute(name="ActiveConsumerCount", description="The total number of active subscribers to the queue")
    Integer getActiveConsumerCount() throws IOException;

    /**
     * Returns the total number of subscribers to the queue.
     * @return the number of subscribers.
     * @throws IOException
     */
    @MBeanAttribute(name="ConsumerCount", description="The total number of subscribers to the queue")
    Integer getConsumerCount() throws IOException;

    /**
     * Tells the Owner of the ManagedQueue.
     * @return the owner's name.
     * @throws IOException
     */
    @MBeanAttribute(name="Owner", description = "Owner")
    String getOwner() throws IOException;

    /**
     * Tells whether this ManagedQueue is durable or not.
     * @return true if this ManagedQueue is a durable queue.
     * @throws IOException
     */
    @MBeanAttribute(name="Durable", description = "true if the AMQQueue is durable")
    boolean isDurable() throws IOException;

    /**
     * Tells if the ManagedQueue is set to AutoDelete.
     * @return  true if the ManagedQueue is set to AutoDelete.
     * @throws IOException
     */
    @MBeanAttribute(name="AutoDelete", description = "true if the AMQQueue is AutoDelete")
    boolean isAutoDelete() throws IOException;

    /**
     * Returns the maximum age of a message (expiration time) in milliseconds
     * @return the maximum age
     * @throws IOException
     */
    Long getMaximumMessageAge() throws IOException;

    /**
     * Sets the maximum age of a message in milliseconds
     * @param age  maximum age of message.
     * @throws IOException
     */
    @MBeanAttribute(name="MaximumMessageAge", description="Threshold high value(milliseconds) for message age")
    void setMaximumMessageAge(Long age) throws IOException;

    /**
     * Returns the maximum size of a message (in Bytes) allowed to be accepted by the
     * ManagedQueue. This is useful in setting notifications or taking
     * appropriate action, if the size of the message received is more than
     * the allowed size.
     * @return the maximum size of a message allowed to be aceepted by the
     *         ManagedQueue.
     * @throws IOException
     */
    Long getMaximumMessageSize() throws IOException;

    /**
     * Sets the maximum size of the message (in Bytes) that is allowed to be
     * accepted by the Queue.
     * @param size  maximum size of message.
     * @throws IOException
     */
    @MBeanAttribute(name="MaximumMessageSize", description="Threshold high value(Bytes) for a message size")
    void setMaximumMessageSize(Long size) throws IOException;

    /**
     * Tells the maximum number of messages that can be stored in the queue.
     * This is useful in setting the notifications or taking required
     * action is the number of message increase this limit.
     * @return maximum muber of message allowed to be stored in the queue.
     * @throws IOException
     */
    Long getMaximumMessageCount() throws IOException;

    /**
     * Sets the maximum number of messages allowed to be stored in the queue.
     * @param value  the maximum number of messages allowed to be stored in the queue.
     * @throws IOException
     */
    @MBeanAttribute(name="MaximumMessageCount", description="Threshold high value for number of undelivered messages in the queue")
    void setMaximumMessageCount(Long value) throws IOException;

    /**
     * This is useful for setting notifications or taking required action if the size of messages
     * stored in the queue increases over this limit.
     * 
     * Since Qpid JMX API 1.2 this operation returns in units of bytes. Prior to this, the result was in units of kilobytes.
     * @return threshold high value for Queue Depth
     * @throws IOException
     */
    Long getMaximumQueueDepth() throws IOException;

    /**
     * Sets the maximum size of all the messages together, that can be stored
     * in the queue.
     * @param value
     * @throws IOException
     */
    @MBeanAttribute(name="MaximumQueueDepth", description="The threshold high value(Bytes) for Queue Depth")
    void setMaximumQueueDepth(Long value) throws IOException;
    
    
    /**
     * Returns the current flow control Capacity of the queue in bytes.
     * 
     * @since Qpid JMX API 1.6
     * @return Capacity at which flow control is enforced
     * @throws IOException
     */
    Long getCapacity() throws IOException;

    /**
     * Sets the Capacity in bytes above which flow is blocked.
     * 
     * @since Qpid JMX API 1.6
     * @param value the capacity in bytes
     * @throws IOException
     * @throws IllegalArgumentException If the given value is less than the queue FloeResumeCapacity
     */
    @MBeanAttribute(name="Capacity", description="The flow control Capacity (Bytes) of the queue")
    void setCapacity(Long value) throws IOException, IllegalArgumentException;
    
    /**
     * Returns the current flow control FlowResumeCapacity of the queue in bytes.
     * 
     * @since Qpid JMX API 1.6
     * @return Capacity below which flow resumes in bytes
     * @throws IOException
     */
    Long getFlowResumeCapacity() throws IOException;

    /**
     * Sets the FlowResumeCapacity in bytes below which flow resumes.
     * 
     * @since Qpid JMX API 1.6
     * @param value of the resume capacity in bytes
     * @throws IOException
     * @throws IllegalArgumentException If the given value exceeds the queue Capacity
     */
    @MBeanAttribute(name="FlowResumeCapacity", description="The flow resume Capacity (Bytes) of the queue")
    void setFlowResumeCapacity(Long value) throws IOException, IllegalArgumentException;

    /**
     * Indicates whether the Queue is currently considered overfull by the FlowControl system
     * 
     * @since Qpid JMX API 1.6
     * @throws IOException
     */
    @MBeanAttribute(name="FlowOverfull", description="true if the queue is considered overfull by the Flow Control system")
    boolean isFlowOverfull() throws IOException;
    
    /**
     * Returns whether the queue is exclusive or not.
     * 
     * @since Qpid JMX API 2.0
     * @return whether the queue is exclusive.
     * @throws IOException
     */
    boolean isExclusive() throws IOException;

    /**
     * Sets whether the queue is exclusive or not.
     * 
     * @since Qpid JMX API 2.0
     * @param exclusive the capacity in bytes
     * @throws IOException
     * @throws JMException 
     */
    @MBeanAttribute(name="Exclusive", description="Whether the queue is Exclusive or not")
    void setExclusive(boolean exclusive) throws IOException, JMException;

    //********** Operations *****************//


    /**
     * Returns a subset of all the messages stored in the queue. The messages
     * are returned based on the given index numbers.
     * 
     * Deprecated as of Qpid JMX API 1.3
     * @param fromIndex
     * @param toIndex
     * @return
     * @throws IOException
     * @throws JMException
     */
    @Deprecated
    @MBeanOperation(name="viewMessages",
                    description="Message headers for messages in this queue within given index range. eg. from index 1 - 100")
    TabularData viewMessages(@MBeanOperationParameter(name="from index", description="from index")int fromIndex,
                             @MBeanOperationParameter(name="to index", description="to index")int toIndex)
            throws IOException, JMException;
    
    /**
     * Returns a subset (up to 2^31 messages at a time) of all the messages stored on the queue. 
     * The messages are returned based on the given queue position range.
     * @param startPosition
     * @param endPosition
     * @return
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="viewMessages",
                    description="Message headers for messages in this queue within given queue positions range. eg. from index 1 - 100")
    TabularData viewMessages(@MBeanOperationParameter(name="start position", description="start position")long startPosition,
                             @MBeanOperationParameter(name="end position", description="end position")long endPosition)
            throws IOException, JMException;

    /**
     * Returns the content for the given AMQ Message ID.
     * 
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="viewMessageContent", description="The message content for given Message Id")
    CompositeData viewMessageContent(@MBeanOperationParameter(name="Message Id", description="Message Id")long messageId)
        throws IOException, JMException;

    /**
     * Deletes the first message from top.
     * 
     * Deprecated as of Qpid JMX API 1.3
     * @throws IOException
     * @throws JMException
     */
    @Deprecated
    @MBeanOperation(name="deleteMessageFromTop", description="Deletes the first message from top",
                    impact= MBeanOperationInfo.ACTION)
    void deleteMessageFromTop() throws IOException, JMException;

    /**
     * Clears the queue by deleting all the messages from the queue that have not been acquired by consumers"
     * 
     * Since Qpid JMX API 1.3 this returns the number of messages deleted. Prior to this, the return type was void.
     * @return the number of messages deleted
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="clearQueue",
                    description="Clears the queue by deleting all the messages from the queue " +
                    		"that have not been acquired by consumers",
                    impact= MBeanOperationInfo.ACTION)
    Long clearQueue() throws IOException, JMException;

    /**
     * Moves the messages in given range of message Ids to given Queue. QPID-170
     * @param fromMessageId  first in the range of message ids
     * @param toMessageId    last in the range of message ids
     * @param toQueue        where the messages are to be moved
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="moveMessages",
                    description="You can move messages to another queue from this queue ",
                    impact= MBeanOperationInfo.ACTION)
    void moveMessages(@MBeanOperationParameter(name="from MessageId", description="from MessageId")long fromMessageId,
                      @MBeanOperationParameter(name="to MessageId", description="to MessageId")long toMessageId,
                      @MBeanOperationParameter(name= ManagedQueue.TYPE, description="to Queue Name")String toQueue)
            throws IOException, JMException;
    
    /**
     * Deletes the messages in given range of AMQ message Ids in the given Queue.
     * @param fromMessageId  first in the range of message ids
     * @param toMessageId    last in the range of message ids
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="deleteMessages",
                    description="Delete a range of messages from a specified queue",
                    impact= MBeanOperationInfo.ACTION)
    void deleteMessages(@MBeanOperationParameter(name="from MessageId", description="from MessageId")long fromMessageId,
                      @MBeanOperationParameter(name="to MessageId", description="to MessageId")long toMessageId)
            throws IOException, JMException;
    
    /**
     * Copies the messages in given range of AMQ message Ids to a given Queue.
     * @param fromMessageId  first in the range of message ids
     * @param toMessageId    last in the range of message ids
     * @param toQueue        where the messages are to be copied
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="copyMessages",
                    description="Copies a range of messages to a specified queue",
                    impact= MBeanOperationInfo.ACTION)
    void copyMessages(@MBeanOperationParameter(name="from MessageId", description="from MessageId")long fromMessageId,
                      @MBeanOperationParameter(name="to MessageId", description="to MessageId")long toMessageId,
                      @MBeanOperationParameter(name= ManagedQueue.TYPE, description="to Queue Name")String toQueue)
            throws IOException, JMException;
}
