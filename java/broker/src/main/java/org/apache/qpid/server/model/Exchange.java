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
import java.util.Map;

public interface Exchange extends ConfiguredObject
{
    String BINDING_COUNT = "bindingCount";
    String BYTES_DROPPED = "bytesDropped";
    String BYTES_IN      = "bytesIn";
    String MESSAGES_DROPPED = "messagesDropped";
    String MESSAGES_IN      = "messagesIn";
    String PRODUCER_COUNT   = "producerCount";
    String STATE_CHANGED    = "stateChanged";

    public static final Collection<String> AVAILABLE_STATISTICS =
            Collections.unmodifiableList(
                    Arrays.asList(BINDING_COUNT,
                                  BYTES_DROPPED,
                                  BYTES_IN,
                                  MESSAGES_DROPPED,
                                  MESSAGES_IN,
                                  PRODUCER_COUNT,
                                  STATE_CHANGED));

    String CREATED                              = "created";
    String DURABLE                              = "durable";
    String ID                                   = "id";
    String LIFETIME_POLICY                      = "lifetimePolicy";
    String NAME                                 = "name";
    String STATE                                = "state";
    String TIME_TO_LIVE                         = "timeToLive";
    String UPDATED                              = "updated";
    String ALTERNATE_EXCHANGE                   = "alternateExchange";
    String TYPE                                 = "type";

    // Attributes
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
                            ALTERNATE_EXCHANGE,
                            TYPE
                    ));

    String getExchangeType();

    //children
    Collection<Binding> getBindings();
    Collection<Publisher> getPublishers();

    //operations
    Binding createBinding(String bindingKey,
                          Queue queue,
                          Map<String,Object> bindingArguments,
                          Map<String, Object> attributes);


    // Statistics

    void delete();
}
