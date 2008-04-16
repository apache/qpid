/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.security.access;

import org.apache.qpid.framing.AMQMethodBody;

import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.AMQConnectionException;
import org.apache.commons.configuration.Configuration;


public interface ACLPlugin
{
    /**
     * Pseudo-Code:
     * Identify requested RighConnectiont
     * Lookup users ability for that right.
     * if rightsExists
     * Validate right on object
     * Return result
     * e.g
     * User, CONSUME , Queue
     * User, CONSUME , Exchange + RoutingKey
     * User, PUBLISH , Exchange + RoutingKey
     * User, CREATE  , Exchange || Queue
     * User, BIND    , Exchange + RoutingKey + Queue
     *
     * @param session      - The session requesting access
     * @param permission   - The permission requested
     * @param parameters   - The above objects that are used to authorise the request.
     * @return The AccessResult decision
     */
    //todo potential refactor this ConnectionException Out of here
    AccessResult authorise(AMQProtocolSession session, Permission permission, AMQMethodBody body, Object... parameters) throws AMQConnectionException;

    String getPluginName();

    void setConfiguaration(Configuration config);

}
