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
package org.apache.qpid.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.address.AddressException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class QpidDestination implements Destination, Referenceable
{
    private static final Logger _logger = LoggerFactory.getLogger(QpidDestination.class);

    public enum DestinationType {QUEUE, TOPIC};

    protected String _destinationString;
    protected Address _address;

    protected QpidDestination()
    {
    }

    public String getDestinationString()
    {
        return _destinationString;
    }

    public void setDestinationString(String str) throws JMSException
    {
        if (_destinationString != null)
        {
            throw new javax.jms.IllegalStateException("Once a destination string is set, it cannot be changed");
        }
        _destinationString = str;
        parseDestinationString(str);
    }

    public abstract DestinationType getType();

    protected void parseDestinationString(String str) throws JMSException
    {
        _address = DestinationStringParser.parseDestinationString(str, getType());
    }

    protected Address getAddress()
    {
        return _address;
    }

    @Override
    public Reference getReference() throws NamingException
    {
        return new Reference(
                this.getClass().getName(),
                new StringRefAddr(this.getClass().getName(), toString()),
                AMQConnectionFactory.class.getName(),
                null);          // factory location
    }

    @Override
    public String toString()
    {
        return _address == null ? "" : _address.toString();
    }
}
