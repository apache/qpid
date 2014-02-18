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
package org.apache.qpid.server.exchange;

import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.store.DurableConfigurationStoreHelper;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class ExchangeInitialiser
{
    public void initialise(ExchangeFactory factory, ExchangeRegistry registry, DurableConfigurationStore store)
    {
        for (ExchangeType<? extends Exchange> type : factory.getRegisteredTypes())
        {
            define (registry, factory, type.getDefaultExchangeName(), type.getType(), store);
        }

    }

    private void define(ExchangeRegistry r, ExchangeFactory f,
                        String name, String type, DurableConfigurationStore store)
    {
        try
        {
            if(r.getExchange(name)== null)
            {
                Exchange exchange = f.createExchange(name, type, true, false);
                r.registerExchange(exchange);
                if(exchange.isDurable())
                {
                    DurableConfigurationStoreHelper.createExchange(store, exchange);
                }
            }
        }
        catch (AMQUnknownExchangeType e)
        {
            throw new ServerScopedRuntimeException("Unknown exchange type while attempting to initialise exchanges - " +
                                                   "this is because necessary jar files are not on the classpath", e);
        }
    }
}
