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
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.queue.QueueEntryVisitor;
import org.apache.qpid.server.store.MessageDurability;

@ManagedObject( defaultType = "standard" )
public interface Queue<X extends Queue<X>> extends ConfiguredObject<X>
{

    String ALERT_REPEAT_GAP = "alertRepeatGap";
    String ALERT_THRESHOLD_MESSAGE_AGE = "alertThresholdMessageAge";
    String ALERT_THRESHOLD_MESSAGE_SIZE = "alertThresholdMessageSize";
    String ALERT_THRESHOLD_QUEUE_DEPTH_BYTES = "alertThresholdQueueDepthBytes";
    String ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES = "alertThresholdQueueDepthMessages";
    String ALTERNATE_EXCHANGE = "alternateExchange";
    String EXCLUSIVE = "exclusive";
    String MESSAGE_DURABILITY = "messageDurability";
    String MESSAGE_GROUP_KEY = "messageGroupKey";
    String MESSAGE_GROUP_SHARED_GROUPS = "messageGroupSharedGroups";
    String MESSAGE_GROUP_DEFAULT_GROUP = "messageGroupDefaultGroup";
    String MAXIMUM_DELIVERY_ATTEMPTS = "maximumDeliveryAttempts";
    String NO_LOCAL = "noLocal";
    String OWNER = "owner";
    String QUEUE_FLOW_CONTROL_SIZE_BYTES = "queueFlowControlSizeBytes";
    String QUEUE_FLOW_RESUME_SIZE_BYTES = "queueFlowResumeSizeBytes";
    String QUEUE_FLOW_STOPPED = "queueFlowStopped";
    String MAXIMUM_MESSAGE_TTL = "maximumMessageTtl";
    String MINIMUM_MESSAGE_TTL = "minimumMessageTtl";
    String DEFAULT_FILTERS = "defaultFilters";

    String QUEUE_MINIMUM_ESTIMATED_MEMORY_FOOTPRINT = "queue.minimumEstimatedMemoryFootprint";
    @ManagedContextDefault( name = QUEUE_MINIMUM_ESTIMATED_MEMORY_FOOTPRINT)
    long DEFAULT_MINIMUM_ESTIMATED_MEMORY_FOOTPRINT = 102400l;

    String QUEUE_ESTIMATED_MESSAGE_MEMORY_OVERHEAD = "queue.estimatedMessageMemoryOverhead";
    @ManagedContextDefault( name = QUEUE_ESTIMATED_MESSAGE_MEMORY_OVERHEAD)
    long DEFAULT_ESTIMATED_MESSAGE_MEMORY_OVERHEAD = 1024l;

    String MAX_ASYNCHRONOUS_DELIVERIES = "queue.maxAsynchronousDeliveries";
    @ManagedContextDefault(name = MAX_ASYNCHRONOUS_DELIVERIES )
    int DEFAULT_MAX_ASYNCHRONOUS_DELIVERIES = 80;

    @ManagedAttribute
    Exchange getAlternateExchange();

    @ManagedAttribute( defaultValue = "NONE" )
    ExclusivityPolicy getExclusive();

    @DerivedAttribute( persist = true )
    String getOwner();

    @ManagedAttribute
    boolean isNoLocal();


    @ManagedAttribute
    String getMessageGroupKey();

    @ManagedContextDefault( name = "qpid.broker_default-shared-message-group")
    String DEFAULT_SHARED_MESSAGE_GROUP = "qpid.no-group";

    @ManagedAttribute( defaultValue = "${qpid.broker_default-shared-message-group}")
    String getMessageGroupDefaultGroup();

    @ManagedContextDefault( name = "queue.maximumDistinctGroups")
    int DEFAULT_MAXIMUM_DISTINCT_GROUPS = 255;

    @ManagedAttribute( defaultValue = "${queue.maximumDistinctGroups}")
    int getMaximumDistinctGroups();

    @ManagedAttribute
    boolean isMessageGroupSharedGroups();

    @ManagedContextDefault( name = "queue.maximumDeliveryAttempts")
    int DEFAULT_MAXIMUM_DELIVERY_ATTEMPTS = 0;

    @ManagedAttribute( defaultValue = "${queue.maximumDeliveryAttempts}")
    int getMaximumDeliveryAttempts();

    @ManagedContextDefault( name = "queue.queueFlowControlSizeBytes")
    long DEFAULT_FLOW_CONTROL_SIZE_BYTES = 0l;

    @ManagedAttribute( defaultValue = "${queue.queueFlowControlSizeBytes}")
    long getQueueFlowControlSizeBytes();

    @ManagedContextDefault( name = "queue.queueFlowResumeSizeBytes")
    long DEFAULT_FLOW_CONTROL_RESUME_SIZE_BYTES = 0l;

    @ManagedAttribute( defaultValue = "${queue.queueFlowResumeSizeBytes}")
    long getQueueFlowResumeSizeBytes();


    // TODO - this is not an attribute
    @DerivedAttribute
    boolean isQueueFlowStopped();

    @ManagedContextDefault( name = "queue.alertThresholdMessageAge")
    long DEFAULT_ALERT_THRESHOLD_MESSAGE_AGE = 0l;

    @ManagedAttribute( defaultValue = "${queue.alertThresholdMessageAge}")
    long getAlertThresholdMessageAge();

    @ManagedContextDefault( name = "queue.alertThresholdMessageSize")
    long DEFAULT_ALERT_THRESHOLD_MESSAGE_SIZE = 0l;

    @ManagedAttribute( defaultValue = "${queue.alertThresholdMessageSize}")
    long getAlertThresholdMessageSize();

    @ManagedContextDefault( name = "queue.alertThresholdQueueDepthBytes")
    long DEFAULT_ALERT_THRESHOLD_QUEUE_DEPTH = 0l;

    @ManagedAttribute( defaultValue = "${queue.alertThresholdQueueDepthBytes}")
    long getAlertThresholdQueueDepthBytes();

    @ManagedContextDefault( name = "queue.alertThresholdQueueDepthMessages")
    long DEFAULT_ALERT_THRESHOLD_MESSAGE_COUNT = 0l;

    @ManagedAttribute( defaultValue = "${queue.alertThresholdQueueDepthMessages}")
    long getAlertThresholdQueueDepthMessages();


    @ManagedContextDefault( name = "queue.alertRepeatGap")
    long DEFAULT_ALERT_REPEAT_GAP = 30000l;

    @ManagedAttribute( defaultValue = "${queue.alertRepeatGap}")
    long getAlertRepeatGap();

    @ManagedAttribute( defaultValue = "DEFAULT" )
    MessageDurability getMessageDurability();

    @ManagedAttribute
    long getMinimumMessageTtl();

    @ManagedAttribute
    long getMaximumMessageTtl();

    @ManagedAttribute
    Map<String, Map<String,List<Object>>> getDefaultFilters();

    //children
    Collection<? extends Binding> getBindings();


    Collection<? extends Consumer> getConsumers();

    //operations

    void visit(QueueEntryVisitor visitor);

    int deleteAndReturnCount();

    void setNotificationListener(QueueNotificationListener listener);

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

    @ManagedStatistic
    long getOldestMessageAge();


}
