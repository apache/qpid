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

public interface ReplicationNode extends ConfiguredObject
{
    String STATE                                = "state";
    String CREATED                              = "created";
    String DURABLE                              = "durable";
    String LIFETIME_POLICY                      = "lifetimePolicy";
    String TIME_TO_LIVE                         = "timeToLive";
    String TYPE                                 = "type";
    String UPDATED                              = "updated";

    /** Name of the group to which this replication node belongs */
    String GROUP_NAME                           = "groupName";

    /** Node host name/IP and port separated by semicolon*/
    String HOST_PORT                            = "hostPort";

    /** Node helper host name/IP and port separated by semicolon*/
    String HELPER_HOST_PORT                     = "helperHostPort";

    /** Durability settings*/
    String DURABILITY                           = "durability";

    /** Sync multiple transactions on disc at the same time*/
    String COALESCING_SYNC                      = "coalescingSync";

    /** A designated primary setting for 2-nodes group*/
    String DESIGNATED_PRIMARY                   = "designatedPrimary";

    /** Node priority. 1 signifies normal priority; 0 signifies node will never be elected. */
    String PRIORITY                             = "priority";

    /** The overridden minimum number of group nodes required to commit transaction on this node instead of simple majority*/
    String QUORUM_OVERRIDE                      = "quorumOverride";

    /** Node role: MASTER,REPLICA,UNKNOWN,DETACHED*/
    String ROLE                                 = "role";

    /** Time when node joined the group */
    String JOIN_TIME                            = "joinTime";

    /** Last known replication transaction id */
    String LAST_KNOWN_REPLICATION_TRANSACTION_ID= "lastKnownReplicationTransactionId";

    /** Map with additional implementation specific node settings */
    String PARAMETERS                           = "parameters";

    /** Map with additional implementation specific replication parameters*/
    String REPLICATION_PARAMETERS               = "replicationParameters";

    /** Store path */
    String STORE_PATH                           = "storePath";

    // Attributes
    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            ID,
                            NAME,
                            TYPE,
                            STATE,
                            DURABLE,
                            LIFETIME_POLICY,
                            TIME_TO_LIVE,
                            CREATED,
                            UPDATED,
                            GROUP_NAME,
                            HOST_PORT,
                            HELPER_HOST_PORT,
                            DURABILITY,
                            COALESCING_SYNC,
                            DESIGNATED_PRIMARY,
                            PRIORITY,
                            QUORUM_OVERRIDE,
                            ROLE,
                            JOIN_TIME,
                            LAST_KNOWN_REPLICATION_TRANSACTION_ID,
                            PARAMETERS,
                            REPLICATION_PARAMETERS,
                            STORE_PATH
                            ));

    public boolean isLocal();
}
