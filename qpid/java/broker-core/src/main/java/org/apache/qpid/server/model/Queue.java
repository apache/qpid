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



    //children
    Collection<? extends Binding> getBindings();

    // TODO - Undo this commented out line when we stop supporting 1.6 for compilation
    //        In 1.6 this causes the build to break at AbstractQueue because the 1.6 compiler can't work out that
    //        the definition in terms of the Consumer implementation meets both this, and the contract for AMQQueue

    // Collection<? extends Consumer> getConsumers();

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

}
