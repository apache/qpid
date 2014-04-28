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
package org.apache.qpid.server.binding;

import java.util.Map;

import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.AbstractConfiguredObjectTypeFactory;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.queue.AMQQueue;

@PluggableService
public class BindingFactory extends AbstractConfiguredObjectTypeFactory<BindingImpl>
{
    public BindingFactory()
    {
        super(BindingImpl.class);
    }

    @Override
    protected BindingImpl createInstance(final Map<String, Object> attributes, final ConfiguredObject<?>... parents)
    {
        ExchangeImpl<?> exchange = (ExchangeImpl<?>) getParent(Exchange.class, parents);
        AMQQueue<?> queue = (AMQQueue<?>) getParent(Queue.class, parents);
        BindingImpl binding = new BindingImpl(attributes, queue, exchange);
        exchange.addBinding(binding);
        return binding;
    }
}
