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
package org.apache.qpid.client.failover;

/**
 * Enumeration of failover states. Used to handle failover from within AMQProtocolHandler where MINA events need to be
 * dealt with and can happen during failover.
 */
public final class FailoverState
{
    private final String _state;

    /** Failover has not yet been attempted on this connection */
    public static final FailoverState NOT_STARTED = new FailoverState("NOT STARTED");

    /** Failover has been requested on this connection but has not completed */
    public static final FailoverState IN_PROGRESS = new FailoverState("IN PROGRESS");

    /** Failover has been attempted and failed */
    public static final FailoverState FAILED = new FailoverState("FAILED");

    private FailoverState(String state)
    {
        _state = state;
    }

    public String toString()
    {
        return "FailoverState: " + _state;
    }
}
