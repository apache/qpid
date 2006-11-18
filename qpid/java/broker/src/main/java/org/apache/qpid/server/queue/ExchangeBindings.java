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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.AMQException;

import java.util.List;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * When a queue is deleted, it should be deregistered from any
 * exchange it has been bound to. This class assists in this task,
 * by keeping track of all bindings for a given queue.
 */
class ExchangeBindings
{
    static class ExchangeBinding
    {
        private final Exchange exchange;
        private final String routingKey;

        ExchangeBinding(String routingKey, Exchange exchange)
        {
            this.routingKey = routingKey;
            this.exchange = exchange;
        }

        void unbind(AMQQueue queue) throws AMQException
        {
            exchange.deregisterQueue(routingKey, queue);
        }

        public Exchange getExchange()
        {
            return exchange;
        }

        public String getRoutingKey()
        {
            return routingKey;
        }

        public int hashCode()
        {
            return exchange.hashCode() + routingKey.hashCode();
        }

        public boolean equals(Object o)
        {
            if (!(o instanceof ExchangeBinding)) return false;
            ExchangeBinding eb = (ExchangeBinding) o;
            return exchange.equals(eb.exchange) && routingKey.equals(eb.routingKey);
        }
    }

    private final List<ExchangeBinding> _bindings = new CopyOnWriteArrayList<ExchangeBinding>();
    private final AMQQueue _queue;

    ExchangeBindings(AMQQueue queue)
    {
        _queue = queue;
    }

    /**
     * Adds the specified binding to those being tracked.
     * @param routingKey the routing key with which the queue whose bindings
     * are being tracked by the instance has been bound to the exchange
     * @param exchange the exchange bound to
     */
    void addBinding(String routingKey, Exchange exchange)
    {
        _bindings.add(new ExchangeBinding(routingKey, exchange));
    }

    /**
     * Deregisters this queue from any exchange it has been bound to
     */
    void deregister() throws AMQException
    {
        //remove duplicates at this point
        HashSet<ExchangeBinding> copy = new HashSet<ExchangeBinding>(_bindings);
        for (ExchangeBinding b : copy)
        {
            b.unbind(_queue);
        }
    }

    List<ExchangeBinding> getExchangeBindings()
    {
        return _bindings;
    }
}
