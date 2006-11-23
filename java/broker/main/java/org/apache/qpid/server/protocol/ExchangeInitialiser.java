/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.protocol;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;

public class ExchangeInitialiser
{
    public void initialise(ExchangeFactory factory, ExchangeRegistry registry) throws AMQException{        
        define(registry, factory, ExchangeDefaults.DIRECT_EXCHANGE_NAME, ExchangeDefaults.DIRECT_EXCHANGE_CLASS);
        define(registry, factory, ExchangeDefaults.TOPIC_EXCHANGE_NAME, ExchangeDefaults.TOPIC_EXCHANGE_CLASS);
        define(registry, factory, ExchangeDefaults.HEADERS_EXCHANGE_NAME, ExchangeDefaults.HEADERS_EXCHANGE_CLASS);
    }

    private void define(ExchangeRegistry r, ExchangeFactory f,
                        String name, String type) throws AMQException
    {
        r.registerExchange(f.createExchange(name, type, true, false, 0));
    }
}
