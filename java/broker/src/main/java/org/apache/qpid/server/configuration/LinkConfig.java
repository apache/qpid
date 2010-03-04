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

package org.apache.qpid.server.configuration;

import org.apache.qpid.server.exchange.ExchangeType;

import java.util.Map;


public interface LinkConfig extends ConfiguredObject<LinkConfigType, LinkConfig>
{
    VirtualHostConfig getVirtualHost();


    String getTransport();

    String getHost();

    int getPort();

    String getRemoteVhost();

    String getAuthMechanism();

    String getUsername();

    String getPassword();

    void close();

    void createBridge(boolean durable,
                      boolean dynamic,
                      boolean srcIsQueue,
                      boolean srcIsLocal,
                      String src,
                      String dest,
                      String key, String tag, String excludes);
}