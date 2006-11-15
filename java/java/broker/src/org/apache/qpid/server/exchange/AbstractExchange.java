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

import org.apache.qpid.AMQException;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.management.Managable;
import org.apache.qpid.server.management.ManagedObject;

import javax.management.NotCompliantMBeanException;

public abstract class AbstractExchange implements Exchange, Managable
{
    private String _name;

    protected boolean _durable;

    protected int _ticket;

    protected ExchangeMBean _exchangeMbean;

    /**
     * Whether the exchange is automatically deleted once all queues have detached from it
     */
    protected boolean _autoDelete;

    /**
     * Abstract MBean class. This has some of the methods implemented from
     * management intrerface for exchanges. Any implementaion of an
     * Exchange MBean should extend this class.
     */
    protected abstract class ExchangeMBean extends AMQManagedObject implements ManagedExchange
    {
        public ExchangeMBean() throws NotCompliantMBeanException
        {
            super(ManagedExchange.class, ManagedExchange.TYPE);
        }

        public String getObjectInstanceName()
        {
            return _name;
        }

        public String getName()
        {
            return _name;
        }

        public Integer getTicketNo()
        {
            return _ticket;
        }

        public boolean isDurable()
        {
            return _durable;
        }

        public boolean isAutoDelete()
        {
            return _autoDelete;
        }

    } // End of MBean class

    public String getName()
    {
        return _name;
    }

    /**
     * Concrete exchanges must implement this method in order to create the managed representation. This is
     * called during initialisation (template method pattern).
     * @return the MBean
     */
    protected abstract ExchangeMBean createMBean() throws AMQException;

    public void initialise(String name, boolean durable, int ticket, boolean autoDelete) throws AMQException
    {
        _name = name;
        _durable = durable;
        _autoDelete = autoDelete;
        _ticket = ticket;
        _exchangeMbean = createMBean();
        _exchangeMbean.register();
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public int getTicket()
    {
        return _ticket;
    }

    public void close() throws AMQException
    {
        if (_exchangeMbean != null)
        {
            _exchangeMbean.unregister();
        }
    }

    public String toString()
    {
        return getClass().getName() + "[" + getName() +"]";
    }

    public ManagedObject getManagedObject()
    {
        return _exchangeMbean;
    }

}
