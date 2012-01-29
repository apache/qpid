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
package org.apache.qpid.common;

/**
 * Keys names used within the serverProperties argument of the ConnectionStart
 * method.  These property names are Qpid specific.
 */
public final class ServerPropertyNames
{
    private ServerPropertyNames()
    {
    }

    /**
     * Server property: federation tag UUID
     */
    public static final String FEDERATION_TAG = "qpid.federation_tag";

    /**
     * Server property: array of features supported by the server.
     */
    public static final String QPID_FEATURES = "qpid.features";

    /**
     * Feature: Signifies that a server supports JMS selectors.
     */
    public static final String FEATURE_QPID_JMS_SELECTOR = "qpid.jms-selector";
}
