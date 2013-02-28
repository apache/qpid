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
 *
 */
package org.apache.qpid.server.model;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public interface GroupProvider extends ConfiguredObject
{
    public static final String ID = "id";
    public static final String DESCRIPTION = "description";
    public static final String NAME = "name";
    public static final String STATE = "state";
    public static final String DURABLE = "durable";
    public static final String LIFETIME_POLICY = "lifetimePolicy";
    public static final String TIME_TO_LIVE = "timeToLive";
    public static final String CREATED = "created";
    public static final String UPDATED = "updated";
    public static final String TYPE = "type";

    public static final Collection<String> AVAILABLE_ATTRIBUTES =
            Collections.unmodifiableList(
                    Arrays.asList(ID,
                                  NAME,
                                  DESCRIPTION,
                                  STATE,
                                  DURABLE,
                                  LIFETIME_POLICY,
                                  TIME_TO_LIVE,
                                  CREATED,
                                  UPDATED,
                                  TYPE));

    Set<Principal> getGroupPrincipalsForUser(String username);
}
