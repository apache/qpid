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
package org.apache.qpid.server.model;

import java.util.Collection;

import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.QueueEntryVisitor;

@ManagedObject

public interface Queue<X extends Queue<X>> extends ConfiguredObject<X>
{

    public static final String DURABLE = "durable";
    public static final String LIFETIME_POLICY = "lifetimePolicy";

    public static final String ALERT_REPEAT_GAP = "alertRepeatGap";
    public static final String ALERT_THRESHOLD_MESSAGE_AGE = "alertThresholdMessageAge";
    public static final String ALERT_THRESHOLD_MESSAGE_SIZE = "alertThresholdMessageSize";
    public static final String ALERT_THRESHOLD_QUEUE_DEPTH_BYTES = "alertThresholdQueueDepthBytes";
    public static final String ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES = "alertThresholdQueueDepthMessages";
    public static final String ALTERNATE_EXCHANGE = "alternateExchange";
    public static final String EXCLUSIVE = "exclusive";
    public static final String MESSAGE_GROUP_KEY = "messageGroupKey";
    public static final String MESSAGE_GROUP_SHARED_GROUPS = "messageGroupSharedGroups";
    public static final String MESSAGE_GROUP_DEFAULT_GROUP = "messageGroupDefaultGroup";
    public static final String LVQ_KEY = "lvqKey";
    public static final String MAXIMUM_DELIVERY_ATTEMPTS = "maximumDeliveryAttempts";
    public static final String NO_LOCAL = "noLocal";
    public static final String OWNER = "owner";
    public static final String QUEUE_FLOW_CONTROL_SIZE_BYTES = "queueFlowControlSizeBytes";
    public static final String QUEUE_FLOW_RESUME_SIZE_BYTES = "queueFlowResumeSizeBytes";
    public static final String QUEUE_FLOW_STOPPED = "queueFlowStopped";
    public static final String SORT_KEY = "sortKey";
    public static final String QUEUE_TYPE = "queueType";
    public static final String PRIORITIES = "priorities";

    public static final String CREATE_DLQ_ON_CREATION = "x-qpid-dlq-enabled"; // TODO - this value should change

    @ManagedAttribute
    String getQueueType();

    @ManagedAttribute
    Exchange getAlternateExchange();

    @ManagedAttribute
    ExclusivityPolicy getExclusive();

    @ManagedAttribute
    String getOwner();

    @ManagedAttribute
    boolean getNoLocal();

    @ManagedAttribute
    String getLvqKey();

    @ManagedAttribute
    String getSortKey();

    @ManagedAttribute
    String getMessageGroupKey();


    // TODO - this should either be a boolean or maybe an enum
    @ManagedAttribute
    int getMessageGroupSharedGroups();


    @ManagedAttribute
    int getMaximumDeliveryAttempts();

    @ManagedAttribute
    long getQueueFlowControlSizeBytes();

    @ManagedAttribute
    long getQueueFlowResumeSizeBytes();


    // TODO - this is not an attribute
    @ManagedAttribute
    boolean isQueueFlowStopped();


    @ManagedAttribute
    long getAlertThresholdMessageAge();

    @ManagedAttribute
    long getAlertThresholdMessageSize();

    @ManagedAttribute
    long getAlertThresholdQueueDepthBytes();

    @ManagedAttribute
    long getAlertThresholdQueueDepthMessages();

    @ManagedAttribute
    long getAlertRepeatGap();

    @ManagedAttribute
    int getPriorities();

    //children
    Collection<? extends Binding> getBindings();

    //operations

    void visit(QueueEntryVisitor visitor);

    int delete();

    void setNotificationListener(QueueNotificationListener listener);

    @ManagedStatistic
    long getBytesIn();

    @ManagedStatistic
    long getBytesOut();

    @ManagedStatistic
    long getMessagesIn();

    @ManagedStatistic
    long getMessagesOut();

    @ManagedStatistic
    int getBindingCount();

    @ManagedStatistic
    int getConsumerCount();

    @ManagedStatistic
    int getConsumerCountWithCredit();

    @ManagedStatistic
    long getPersistentDequeuedBytes();

    @ManagedStatistic
    long getPersistentDequeuedMessages();

    @ManagedStatistic
    long getPersistentEnqueuedBytes();

    @ManagedStatistic
    long getPersistentEnqueuedMessages();

    @ManagedStatistic
    long getQueueDepthBytes();

    @ManagedStatistic
    int getQueueDepthMessages();

    @ManagedStatistic
    long getTotalDequeuedBytes();

    @ManagedStatistic
    long getTotalDequeuedMessages();

    @ManagedStatistic
    long getTotalEnqueuedBytes();

    @ManagedStatistic
    long getTotalEnqueuedMessages();

    @ManagedStatistic
    long getUnacknowledgedBytes();

    @ManagedStatistic
    long getUnacknowledgedMessages();

}
