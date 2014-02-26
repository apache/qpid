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

import java.util.Collection;
import java.util.UUID;


public interface ExchangeRegistry
{
    void registerExchange(ExchangeImpl exchange);

    ExchangeImpl getDefaultExchange();

    void initialise(ExchangeFactory exchangeFactory);

    ExchangeImpl getExchange(String exchangeName);

    /**
     * Unregister an exchange
     * @param exchange name of the exchange to delete
     * @param ifUnused if true, do NOT delete the exchange if it is in use (has queues bound to it)
     */
    boolean unregisterExchange(String exchange, boolean ifUnused);

    void clearAndUnregisterMbeans();

    ExchangeImpl getExchange(UUID exchangeId);

    Collection<ExchangeImpl> getExchanges();

    Collection<NonDefaultExchange> getExchangesExceptDefault();

    void addRegistryChangeListener(RegistryChangeListener listener);

    /**
     * Validates the name of user custom exchange.
     * <p>
     * Return true if the exchange name is reserved and false otherwise.
     */
    boolean isReservedExchangeName(String name);

    interface RegistryChangeListener
    {
        void exchangeRegistered(ExchangeImpl exchange);
        void exchangeUnregistered(ExchangeImpl exchange);
    }
}
