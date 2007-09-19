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
package org.apache.qpidity.njms;

import org.apache.qpidity.QpidException;
import org.apache.qpidity.url.BindingURL;
import org.apache.qpidity.url.BindingURLImpl;
import org.apache.qpidity.url.URLSyntaxException;
import org.apache.qpid.url.URLHelper;

import javax.jms.Destination;
import javax.naming.Reference;
import javax.naming.NamingException;
import javax.naming.StringRefAddr;
import javax.naming.Referenceable;

/**
 * Implementation of the JMS Destination interface
 */
public class DestinationImpl implements Destination, Referenceable
{
    /**
     * The destination's name
     */
    protected String _destinationName = null;

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
    
    protected String _routingKey;

    /**
     * The biding URL used to create this destiantion
     */
    protected BindingURL _url;

    //--- Constructor

    protected DestinationImpl(String name) throws QpidException
    {
       _queueName = name;
       _routingKey = name;
    }

    /**
     * Create a destiantion from a binding URL
     *
     * @param binding The URL
     * @throws QpidException If the URL is not valid
     */
    public DestinationImpl(BindingURL binding) throws QpidException
    {
        _exchangeName = binding.getExchangeName();
        _exchangeType = binding.getExchangeClass();
        _destinationName = binding.getDestinationName();
        _isExclusive = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCLUSIVE));
        _isAutoDelete = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_AUTODELETE));
        _isDurable = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_DURABLE));
        _queueName = binding.getQueueName();
        _routingKey = binding.getQueueName();
        _url = binding;
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

    public String getRoutingKey()
    {
        return _routingKey;
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

    //----- Interface Referenceable
    public Reference getReference() throws NamingException
    {
        return new Reference(this.getClass().getName(), new StringRefAddr(this.getClass().getName(), toURL()),
                             ConnectionFactoryImpl.class.getName(), // factory
                             null);          // factory location
    }

    //--- non public method s

    /**
     * Get the URL used to create this destiantion
     *
     * @return The URL used to create this destiantion
     */
    public String toURL()
    {
        if (_url == null)
        {
            StringBuffer sb = new StringBuffer();
            sb.append(_exchangeType);
            sb.append("://");
            sb.append(_exchangeName);
            sb.append('/');
            if (_destinationName != null)
            {
                sb.append(_destinationName);
            }
            sb.append('/');
            if (_queueName != null)
            {
                sb.append(_queueName);
            }
            sb.append('?');
            if (_isDurable)
            {
                sb.append(org.apache.qpid.url.BindingURL.OPTION_DURABLE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }
            if (_isExclusive)
            {
                sb.append(org.apache.qpid.url.BindingURL.OPTION_EXCLUSIVE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }
            if (_isAutoDelete)
            {
                sb.append(org.apache.qpid.url.BindingURL.OPTION_AUTODELETE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }
            //removeKey the last char '?' if there is no options , ',' if there are.
            sb.deleteCharAt(sb.length() - 1);
            try
            {
                _url = new BindingURLImpl(sb.toString());
            }
            catch (URLSyntaxException e)
            {
                // this should not happen.
            }
        }
        return _url.getURL();
    }
}

