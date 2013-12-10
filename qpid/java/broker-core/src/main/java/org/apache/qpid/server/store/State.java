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
package org.apache.qpid.server.store;

public enum State
{
    /** The initial state of the store.  In practice, the store immediately transitions to the subsequent states. */
    INITIAL,

    INITIALISING,
    /**
     * The initial set-up of the store has completed.
     * If the store is persistent, it has not yet loaded configuration from disk.
     *
     * From the point of view of the user, the store is essentially stopped.
     */
    INITIALISED,

    ACTIVATING,
    ACTIVE,

    CLOSING,
    CLOSED,

    QUIESCING,
    /** The virtual host (and implicitly also the store) has been manually paused by the user to allow configuration changes to take place */
    QUIESCED;

}