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

import java.util.Collection;
import java.util.Map;

import org.apache.qpid.server.message.MessageDestination;

@ManagedObject
public interface Exchange<X extends Exchange<X>> extends ConfiguredObject<X>, MessageDestination
{
    String ALTERNATE_EXCHANGE                   = "alternateExchange";

    // Attributes

    @ManagedAttribute(automate = true)
    Exchange<?> getAlternateExchange();

    //children
    Collection<? extends Binding> getBindings();
    Collection<Publisher> getPublishers();

    // Statistics
    @ManagedStatistic
    long getBindingCount();

    @ManagedStatistic
    long getBytesDropped();

    @ManagedStatistic
    long getBytesIn();

    @ManagedStatistic
    long getMessagesDropped();

    @ManagedStatistic
    long getMessagesIn();


    //operations
    Binding createBinding(String bindingKey,
                          Queue queue,
                          Map<String,Object> bindingArguments,
                          Map<String, Object> attributes);



    void delete();
}
