/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpidity.jms;

import org.apache.qpidity.QpidException;
import org.apache.qpidity.url.BindingURL;

import javax.jms.Destination;

/**
 * Implementation of the JMS Destination interface
 */
public class DestinationImpl implements Destination
{
    /**
     * The destination's name
     */
    protected String _destinationName = null;

    /**
     * The session used to create this destination
     */
    protected SessionImpl _session;

    /**
     * The excahnge name
     */
    protected String _exchangeName;

    /**
     * The excahnge class
     */
    protected String _exchangeType;

    /**
     * The queue name
     */
    protected String _queueName;

    /**
     * Indicate whether this destination is exclusive
     */
    protected boolean _isExclusive;

    /**
     * Indicates whether this destination is auto delete.
     */
    protected boolean _isAutoDelete;

    /**
     * Indicates whether this destination is durable
     */
    protected boolean _isDurable;

    //--- Constructor
    /**
     * Create a new DestinationImpl.
     *
     * @param session The session used to create this DestinationImpl.
     */
    protected DestinationImpl(SessionImpl session)
    {
       _session = session;
    }


    /**
     * Create a destiantion from a binding URL
     *
     * @param session The session used to create this queue.
     * @param binding The URL
     * @throws QpidException If the URL is not valid
     */
    protected DestinationImpl(SessionImpl session, BindingURL binding) throws QpidException
    {
        _session = session;
        _exchangeName = binding.getExchangeName();
        _exchangeType = binding.getExchangeClass();
        _destinationName = binding.getDestinationName();
        _isExclusive = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCLUSIVE));
        _isAutoDelete = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_AUTODELETE));
        _isDurable = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_DURABLE));
        _queueName = binding.getQueueName();
    }

    //---- Getters and Setters
    /**
     * Overrides Object.toString();
     *
     * @return Stringified destination representation.
     */
    public String toString()
    {
        return _destinationName;
    }

    /**
     * Get the destination name.
     *
     * @return The destination name
     */
    public String getDestinationName()
    {
        return _destinationName;
    }

    /**
     * Get the session of this destination
     *
     * @return The session of this destination
     */
    public SessionImpl getSession()
    {
        return _session;
    }

    /**
     * The exchange name
     *
     * @return The exchange name
     */
    public String getExchangeName()
    {
        return _exchangeName;
    }

    /**
     * The exchange type.
     *
     * @return The exchange type.
     */
    public String getExchangeType()
    {
        return _exchangeType;
    }

    /**
     * The queue name.
     *
     * @return The queue name.
     */
    public String getQpidQueueName()
    {
        return _queueName;
    }

    /**
     * Indicates whether this destination is exclusive.
     *
     * @return true if this destination is exclusive.
     */
    public boolean isExclusive()
    {
        return _isExclusive;
    }

    /**
     * Indicates whether this destination is AutoDelete.
     *
     * @return true if this destination is AutoDelete.
     */
    public boolean isAutoDelete()
    {
        return _isAutoDelete;
    }

    /**
     * Indicates whether this destination is Durable.
     *
     * @return true if this destination is Durable.
     */
    public boolean isDurable()
    {
        return _isDurable;
    }
}

