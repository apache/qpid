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

public interface TrustStore extends ConfiguredObject
{
    String ID = "id";
    String NAME = "name";
    String DURABLE = "durable";
    String LIFETIME_POLICY = "lifetimePolicy";
    String STATE = "state";
    String TIME_TO_LIVE = "timeToLive";
    String CREATED = "created";
    String UPDATED = "updated";
    String DESCRIPTION = "description";

    String PATH = "path";
    String PASSWORD = "password";
    String TYPE = "type";
    String KEY_MANAGER_FACTORY_ALGORITHM = "keyManagerFactoryAlgorithm";

    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                Arrays.asList(
                              ID,
                              NAME,
                              STATE,
                              DURABLE,
                              LIFETIME_POLICY,
                              TIME_TO_LIVE,
                              CREATED,
                              UPDATED,
                              DESCRIPTION,
                              PATH,
                              PASSWORD,
                              TYPE,
                              KEY_MANAGER_FACTORY_ALGORITHM
                              ));

    public String getPassword();

    public void setPassword(String password);
}
