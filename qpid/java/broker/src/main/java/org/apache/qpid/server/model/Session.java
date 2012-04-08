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

public interface Session extends ConfiguredObject
{
    // Statistics

    public static final String             BYTES_IN             = "bytesIn";
    public static final String             BYTES_OUT            = "bytesOut";
    public static final String             CONSUMER_COUNT       = "consumerCount";
    public static final String             LOCAL_TRANSACTION_BEGINS = "localTransactionBegins";
    public static final String             LOCAL_TRANSACTION_OPEN   = "localTransactionOpen";
    public static final String             LOCAL_TRANSACTION_ROLLBACKS = "localTransactionRollbacks";
    public static final String             STATE_CHANGED               = "stateChanged";
    public static final String             UNACKNOWLEDGED_BYTES        = "unacknowledgedBytes";
    public static final String             UNACKNOWLEDGED_MESSAGES     = "unacknowledgedMessages";
    public static final String             XA_TRANSACTION_BRANCH_ENDS  = "xaTransactionBranchEnds";
    public static final String             XA_TRANSACTION_BRANCH_STARTS = "xaTransactionBranchStarts";
    public static final String             XA_TRANSACTION_BRANCH_SUSPENDS = "xaTransactionBranchSuspends";

    public static final Collection<String> AVAILABLE_STATISTICS =
            Collections.unmodifiableCollection(Arrays.asList(BYTES_IN, BYTES_OUT, CONSUMER_COUNT,
                                                             LOCAL_TRANSACTION_BEGINS,
                                                             LOCAL_TRANSACTION_OPEN,
                                                             LOCAL_TRANSACTION_ROLLBACKS, STATE_CHANGED,
                                                             UNACKNOWLEDGED_BYTES, UNACKNOWLEDGED_MESSAGES,
                                                             XA_TRANSACTION_BRANCH_ENDS, XA_TRANSACTION_BRANCH_STARTS,
                                                             XA_TRANSACTION_BRANCH_SUSPENDS));


    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String STATE = "state";
    public static final String DURABLE = "durable";
    public static final String LIFETIME_POLICY = "lifetimePolicy";
    public static final String TIME_TO_LIVE = "timeToLive";
    public static final String CREATED = "created";
    public static final String UPDATED = "updated";

    public static final String CHANNEL_ID = "channelId";

    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableCollection(Arrays.asList(ID,
                                                             NAME,
                                                             STATE,
                                                             DURABLE,
                                                             LIFETIME_POLICY,
                                                             TIME_TO_LIVE,
                                                             CREATED,
                                                             UPDATED,
                                                             CHANNEL_ID));


    Collection<Consumer> getSubscriptions();
    Collection<Publisher> getPublishers();
}
