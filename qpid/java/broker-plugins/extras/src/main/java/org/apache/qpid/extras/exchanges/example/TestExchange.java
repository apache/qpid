package org.apache.qpid.extras.exchanges.example;
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


import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.ExchangeConfigType;
import org.apache.qpid.server.configuration.VirtualHostConfig;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeReferrer;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class TestExchange implements Exchange
{

    public void close() throws AMQException
    {
    }



    public void addBindingListener(final BindingListener listener)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void removeBindingListener(final BindingListener listener)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public AMQShortString getNameShortString()
    {
        return null;
    }

    public AMQShortString getTypeShortString()
    {
        return null;
    }

    public boolean hasBindings()
    {
        return false;
    }

    public boolean isBound(String bindingKey, AMQQueue queue)
    {
        return false;
    }

    public boolean isBound(String bindingKey)
    {
        return false;
    }

    public void addCloseTask(final Task task)
    {

    }

    public void removeCloseTask(final Task task)
    {

    }

    public Exchange getAlternateExchange()
    {
        return null;
    }

    public Map<String, Object> getArguments()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getBindingCount()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getBindingCountHigh()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getMsgReceives()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getMsgRoutes()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getByteReceives()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getByteRoutes()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getCreateTime()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setAlternateExchange(Exchange exchange)
    {

    }

    public void removeReference(ExchangeReferrer exchange)
    {

    }

    public void addReference(ExchangeReferrer exchange)
    {

    }

    public boolean hasReferrers()
    {
        return false;
    }

    public void addBinding(final Binding binding)
    {

    }

    public void removeBinding(final Binding binding)
    {

    }

    public Collection<Binding> getBindings()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void initialise(VirtualHost host, AMQShortString name, boolean durable, boolean autoDelete)
            throws AMQException
    {
    }

    public VirtualHostConfig getVirtualHost()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getName()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public ExchangeType getType()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isAutoDelete()
    {
        return false;
    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return false;
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        return false;
    }

    public boolean isBound(AMQShortString routingKey)
    {
        return false;
    }

    public boolean isBound(AMQQueue queue)
    {
        return false;
    }

    public UUID getId()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public ExchangeConfigType getConfigType()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public ConfiguredObject getParent()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isDurable()
    {
        return false;
    }

    public ArrayList<? extends BaseQueue> route(InboundMessage message)
    {
        return new ArrayList<AMQQueue>();
    }

    public int getTicket()
    {
        return 0;
    }

    public void initialise(VirtualHost arg0, AMQShortString arg1, boolean arg2, int arg3, boolean arg4)
            throws AMQException
    {
    }
}
