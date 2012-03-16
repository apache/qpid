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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.apache.qpid.server.queue.QueueEntryVisitor;


public interface Queue extends ConfiguredObject
{
    public static final String BINDING_COUNT = "bindingCount";
    public static final String CONSUMER_COUNT = "consumerCount";
    public static final String CONSUMER_COUNT_WITH_CREDIT = "consumerCountWithCredit";
    public static final String DISCARDS_TTL_BYTES = "discardsTtlBytes";
    public static final String DISCARDS_TTL_MESSAGES = "discardsTtlMessages";
    public static final String PERSISTENT_DEQUEUED_BYTES = "persistentDequeuedBytes";
    public static final String PERSISTENT_DEQUEUED_MESSAGES = "persistentDequeuedMessages";
    public static final String PERSISTENT_ENQUEUED_BYTES = "persistentEnqueuedBytes";
    public static final String PERSISTENT_ENQUEUED_MESSAGES = "persistentEnqueuedMessages";
    public static final String QUEUE_DEPTH_BYTES = "queueDepthBytes";
    public static final String QUEUE_DEPTH_MESSAGES = "queueDepthMessages";
    public static final String STATE_CHANGED = "stateChanged";
    public static final String TOTAL_DEQUEUED_BYTES = "totalDequeuedBytes";
    public static final String TOTAL_DEQUEUED_MESSAGES = "totalDequeuedMessages";
    public static final String TOTAL_ENQUEUED_BYTES = "totalEnqueuedBytes";
    public static final String TOTAL_ENQUEUED_MESSAGES = "totalEnqueuedMessages";
    public static final String UNACKNOWLEDGED_BYTES = "unacknowledgedBytes";
    public static final String UNACKNOWLEDGED_MESSAGES = "unacknowledgedMessages";

    public static final Collection<String> AVAILABLE_STATISTICS =
            Collections.unmodifiableList(
                    Arrays.asList(BINDING_COUNT,
                                  CONSUMER_COUNT,
                                  CONSUMER_COUNT_WITH_CREDIT,
                                  DISCARDS_TTL_BYTES,
                                  DISCARDS_TTL_MESSAGES,
                                  PERSISTENT_DEQUEUED_BYTES,
                                  PERSISTENT_DEQUEUED_MESSAGES,
                                  PERSISTENT_ENQUEUED_BYTES,
                                  PERSISTENT_ENQUEUED_MESSAGES,
                                  QUEUE_DEPTH_BYTES,
                                  QUEUE_DEPTH_MESSAGES,
                                  STATE_CHANGED,
                                  TOTAL_DEQUEUED_BYTES,
                                  TOTAL_DEQUEUED_MESSAGES,
                                  TOTAL_ENQUEUED_BYTES,
                                  TOTAL_ENQUEUED_MESSAGES,
                                  UNACKNOWLEDGED_BYTES,
                                  UNACKNOWLEDGED_MESSAGES));


    public static final String ALERT_REPEAT_GAP = "alertRepeatGap";
    public static final String ALERT_THRESHOLD_MESSAGE_AGE = "alertThresholdMessageAge";
    public static final String ALERT_THRESHOLD_MESSAGE_SIZE = "alertThresholdMessageSize";
    public static final String ALERT_THRESHOLD_QUEUE_DEPTH_BYTES = "alertThresholdQueueDepthBytes";
    public static final String ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES = "alertThresholdQueueDepthMessages";
    public static final String ALTERNATE_EXCHANGE = "alternateExchange";
    public static final String EXCLUSIVE = "exclusive";
    public static final String MESSAGE_GROUP_KEY = "messageGroupKey";
    public static final String MESSAGE_GROUP_DEFAULT_GROUP = "messageGroupDefaultGroup";
    public static final String MESSAGE_GROUP_SHARED_GROUPS = "messageGroupSharedGroups";
    public static final String LVQ_KEY = "lvqKey";
    public static final String MAXIMUM_DELIVERY_ATTEMPTS = "maximumDeliveryAttempts";
    public static final String NO_LOCAL = "noLocal";
    public static final String OWNER = "owner";
    public static final String QUEUE_FLOW_CONTROL_SIZE_BYTES = "queueFlowControlSizeBytes";
    public static final String QUEUE_FLOW_RESUME_SIZE_BYTES = "queueFlowResumeSizeBytes";
    public static final String QUEUE_FLOW_STOPPED = "queueFlowStopped";
    public static final String SORT_KEY = "sortKey";
    public static final String TYPE = "type";

    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                    Arrays.asList(ALERT_REPEAT_GAP,
                            ALERT_THRESHOLD_MESSAGE_AGE,
                            ALERT_THRESHOLD_MESSAGE_SIZE,
                            ALERT_THRESHOLD_QUEUE_DEPTH_BYTES,
                            ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES,
                            ALTERNATE_EXCHANGE,
                            EXCLUSIVE,
                            MESSAGE_GROUP_KEY,
                            MESSAGE_GROUP_DEFAULT_GROUP,
                            MESSAGE_GROUP_SHARED_GROUPS,
                            LVQ_KEY,
                            MAXIMUM_DELIVERY_ATTEMPTS,
                            NO_LOCAL,
                            OWNER,
                            QUEUE_FLOW_CONTROL_SIZE_BYTES,
                            QUEUE_FLOW_RESUME_SIZE_BYTES,
                            QUEUE_FLOW_STOPPED,
                            SORT_KEY,
                            TYPE));

    //children
    Collection<Binding> getBindings();
    Collection<Subscription> getSubscriptions();


    //operations

    void visit(QueueEntryVisitor visitor);
}
