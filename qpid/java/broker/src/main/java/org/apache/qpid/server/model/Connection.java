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

public interface Connection extends ConfiguredObject
{

    // Statistics

    String BYTES_IN = "bytesIn";
    String BYTES_OUT = "bytesOut";
    String LAST_IO_TIME = "lastIoTime";
    String LOCAL_TRANSACTION_BEGINS = "localTransactionBegins";
    String LOCAL_TRANSACTION_ROLLBACKS = "localTransactionRollbacks";
    String MESSAGES_IN                 = "messagesIn";
    String MESSAGES_OUT                = "messagesOut";
    String SESSION_COUNT               = "sessionCount";
    String STATE_CHANGED               = "stateChanged";
    String XA_TRANSACTION_BRANCH_ENDS  = "xaTransactionBranchEnds";
    String XA_TRANSACTION_BRANCH_STARTS = "xaTransactionBranchStarts";
    String XA_TRANSACTION_BRANCH_SUSPENDS = "xaTransactionBranchSuspends";

    public static final Collection<String> AVAILABLE_STATISTICS =
            Collections.unmodifiableCollection(
                    Arrays.asList(BYTES_IN,
                                  BYTES_OUT,
                                  LAST_IO_TIME,
                                  LOCAL_TRANSACTION_BEGINS,
                                  LOCAL_TRANSACTION_ROLLBACKS,
                                  MESSAGES_IN,
                                  MESSAGES_OUT,
                                  SESSION_COUNT,
                                  STATE_CHANGED,
                                  XA_TRANSACTION_BRANCH_ENDS,
                                  XA_TRANSACTION_BRANCH_STARTS,
                                  XA_TRANSACTION_BRANCH_SUSPENDS));

                            // Attributes

    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_VERSION = "clientVersion";
    public static final String INCOMING = "incoming";
    public static final String LOCAL_ADDRESS = "localAddress";
    public static final String PRINCIPAL = "principal";
    public static final String PROPERTIES = "properties";
    public static final String REMOTE_ADDRESS = "remoteAddress";
    public static final String REMOTE_PROCESS_NAME = "remoteProcessName";
    public static final String REMOTE_PROCESS_PID = "remoteProcessPid";
    public static final String SESSION_COUNT_LIMIT = "sessionCountLimit";

    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableCollection(
                    Arrays.asList(  CLIENT_ID,
                                    CLIENT_VERSION,
                                    INCOMING,
                                    LOCAL_ADDRESS,
                                    PRINCIPAL,
                                    PROPERTIES,
                                    REMOTE_ADDRESS,
                                    REMOTE_PROCESS_NAME,
                                    REMOTE_PROCESS_PID,
                                    SESSION_COUNT_LIMIT));

    //children
    Collection<Session> getSessions();

    void delete();
}
