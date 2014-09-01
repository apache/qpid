/*
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
 */

package org.apache.qpid.server.virtualhostnode.berkeleydb;

import com.sleepycat.je.rep.ReplicatedEnvironment.State;

public enum NodeRole
{
    /** Node is master. */
    MASTER,
    /** Node is replica. */
    REPLICA,
    /** Node is awaiting an election result, or may be awaiting more nodes to join in order that an election may be held.  */
    WAITING,
    /**
     * (Remote) node is unreachable.  Its virtual host node may be stopped, its Broker down,  or a network problem may
     * be preventing it from being contacted.
     */
    UNREACHABLE,
    /** (Local) node is not connected to the group */
    DETACHED;

    public static NodeRole fromJeState(final State state)
    {
        switch(state)
        {
            case DETACHED:
                return DETACHED;
            case UNKNOWN:
                return WAITING;
            case MASTER:
                return MASTER;
            case REPLICA:
                return REPLICA;
            default:
                throw new IllegalArgumentException("Unrecognised JE node state " + state);
        }
    }
}
