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
package org.apache.qpid.client;

import java.net.URISyntaxException;

import javax.jms.Destination;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;
import org.apache.qpid.url.URLHelper;


public abstract class AMQDestination implements Destination, Referenceable
{
    protected final AMQShortString _exchangeName;

    protected final AMQShortString _exchangeClass;

    protected final boolean _isDurable;

    protected final boolean _isExclusive;

    protected final boolean _isAutoDelete;

    private final boolean _browseOnly;

    private AMQShortString _queueName;

    private AMQShortString _routingKey;

    private AMQShortString[] _bindingKeys;

    private Integer _maxDeliveryCount;

    private String _url;
    private AMQShortString _urlAsShortString;

    private boolean _checkedForQueueBinding;

    private boolean _exchangeExistsChecked;

    public static final int QUEUE_TYPE = 1;
    public static final int TOPIC_TYPE = 2;
    public static final int UNKNOWN_TYPE = 3;

    protected AMQDestination(String url) throws URISyntaxException
    {
        this(new AMQBindingURL(url));
    }

    protected AMQDestination(BindingURL binding)
    {
        _exchangeName = binding.getExchangeName();
        _exchangeClass = binding.getExchangeClass();

        _isExclusive = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCLUSIVE));
        _isAutoDelete = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_AUTODELETE));
        _isDurable = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_DURABLE));
        _browseOnly = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_BROWSE));
        _queueName = binding.getQueueName() == null ? null : binding.getQueueName();
        _routingKey = binding.getRoutingKey() == null ? null : binding.getRoutingKey();
        _bindingKeys = binding.getBindingKeys() == null || binding.getBindingKeys().length == 0 ? new AMQShortString[0] : binding.getBindingKeys();
        String count = binding.getOption(BindingURL.OPTION_MAX_DELIVERY_COUNT);
        _maxDeliveryCount = count == null ? null : Integer.parseInt(count);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, AMQShortString queueName)
    {
        this(exchangeName, exchangeClass, routingKey, false, false, queueName, null);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, AMQShortString queueName, AMQShortString[] bindingKeys)
    {
        this(exchangeName, exchangeClass, routingKey, false, false, queueName,bindingKeys);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString destinationName)
    {
        this(exchangeName, exchangeClass, destinationName, false, false, null,null);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
            boolean isAutoDelete, AMQShortString queueName)
    {
        this(exchangeName, exchangeClass, routingKey, isExclusive, isAutoDelete, queueName, false,null);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
                             boolean isAutoDelete, AMQShortString queueName,AMQShortString[] bindingKeys)
    {
        this(exchangeName, exchangeClass, routingKey, isExclusive, isAutoDelete, queueName, false,bindingKeys);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
            boolean isAutoDelete, AMQShortString queueName, boolean isDurable){
        this (exchangeName, exchangeClass, routingKey, isExclusive,isAutoDelete,queueName,isDurable,null);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
                             boolean isAutoDelete, AMQShortString queueName, boolean isDurable,AMQShortString[] bindingKeys)
    {
        this (exchangeName, exchangeClass, routingKey, isExclusive,isAutoDelete,queueName,isDurable,bindingKeys, false);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
            boolean isAutoDelete, AMQShortString queueName, boolean isDurable,AMQShortString[] bindingKeys, boolean browseOnly)
    {
        this (exchangeName, exchangeClass, routingKey, isExclusive,isAutoDelete,queueName,isDurable,bindingKeys, browseOnly, null);
    }
    
    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
                             boolean isAutoDelete, AMQShortString queueName, boolean isDurable,AMQShortString[] bindingKeys, 
                                 boolean browseOnly, Integer maxDeliveryCount)
    {
        if ( (ExchangeDefaults.DIRECT_EXCHANGE_CLASS.equals(exchangeClass) || 
              ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(exchangeClass))  
              && routingKey == null)
        {
            throw new IllegalArgumentException("routing/binding key  must not be null");
        }
        if (exchangeName == null)
        {
            throw new IllegalArgumentException("Exchange name must not be null");
        }
        if (exchangeClass == null)
        {
            throw new IllegalArgumentException("Exchange class must not be null");
        }
        _exchangeName = exchangeName;
        _exchangeClass = exchangeClass;
        _routingKey = routingKey;
        _isExclusive = isExclusive;
        _isAutoDelete = isAutoDelete;
        _queueName = queueName;
        _isDurable = isDurable;
        _bindingKeys = bindingKeys == null || bindingKeys.length == 0 ? new AMQShortString[0] : bindingKeys;
        _browseOnly = browseOnly;
        _maxDeliveryCount = maxDeliveryCount;
    }

    public AMQShortString getEncodedName()
    {
        if(_urlAsShortString == null)
        {
            toURL();
        }
        return _urlAsShortString;
    }

    public boolean isDurable()
    {
        return _isDurable;
    }

    public AMQShortString getExchangeName()
    {
        return _exchangeName;
    }

    public AMQShortString getExchangeClass()
    {
        return _exchangeClass;
    }

    public boolean isTopic()
    {
        return ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(_exchangeClass);
    }

    public boolean isQueue()
    {
        return ExchangeDefaults.DIRECT_EXCHANGE_CLASS.equals(_exchangeClass);
    }

    public String getQueueName()
    {
        return _queueName == null ? null : _queueName.toString();
    }

    public AMQShortString getAMQQueueName()
    {
        return _queueName;
    }

    public void setQueueName(AMQShortString queueName)
    {

        _queueName = queueName;
        // calculated URL now out of date
        _url = null;
        _urlAsShortString = null;
    }

    public AMQShortString getRoutingKey()
    {
        return _routingKey;
    }

    public AMQShortString[] getBindingKeys()
    {
        if (_bindingKeys != null && _bindingKeys.length > 0)
        {
            return _bindingKeys;
        }
        else
        {
            // catering to the common use case where the
            //routingKey is the same as the bindingKey.
            return new AMQShortString[]{_routingKey};
        }
    }

    public boolean isExclusive()
    {
        return _isExclusive;
    }

    public boolean isAutoDelete()
    {
        return _isAutoDelete;
    }

    public abstract boolean isNameRequired();

    public String toString()
    {
        return toURL();

    }

    public boolean isCheckedForQueueBinding()
    {
        return _checkedForQueueBinding;
    }

    public void setCheckedForQueueBinding(boolean checkedForQueueBinding)
    {
        _checkedForQueueBinding = checkedForQueueBinding;
    }


    public boolean isExchangeExistsChecked()
    {
        return _exchangeExistsChecked;
    }

    public void setExchangeExistsChecked(final boolean exchangeExistsChecked)
    {
        _exchangeExistsChecked = exchangeExistsChecked;
    }

    public String toURL()
    {
        String url = _url;
        if(url == null)
        {


            StringBuffer sb = new StringBuffer();

            sb.append(_exchangeClass);
            sb.append("://");
            sb.append(_exchangeName);

            sb.append("/"+_routingKey+"/");

            if (_queueName != null)
            {
                sb.append(_queueName);
            }

            sb.append('?');

            if (_routingKey != null)
            {
                sb.append(BindingURL.OPTION_ROUTING_KEY);
                sb.append("='");
                sb.append(_routingKey).append("'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            // We can't allow both routingKey and bindingKey
            if (_routingKey == null && _bindingKeys != null && _bindingKeys.length>0)
            {

                for (AMQShortString bindingKey:_bindingKeys)
                {
                    sb.append(BindingURL.OPTION_BINDING_KEY);
                    sb.append("='");
                    sb.append(bindingKey);
                    sb.append("'");
                    sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
                }
            }

            if (_isDurable)
            {
                sb.append(BindingURL.OPTION_DURABLE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_isExclusive)
            {
                sb.append(BindingURL.OPTION_EXCLUSIVE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_isAutoDelete)
            {
                sb.append(BindingURL.OPTION_AUTODELETE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }
            
            
            if (_maxDeliveryCount != null)
            {
                sb.append(BindingURL.OPTION_MAX_DELIVERY_COUNT);
                sb.append("='" + _maxDeliveryCount + "'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            //removeKey the last char '?' if there is no options , ',' if there are.
            sb.deleteCharAt(sb.length() - 1);
            url = sb.toString();
            _url = url;
            _urlAsShortString = new AMQShortString(url);
        }
        return url;
    }

    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final AMQDestination that = (AMQDestination) o;

        if (!_exchangeClass.equals(that._exchangeClass))
        {
            return false;
        }
        if (!_exchangeName.equals(that._exchangeName))
        {
            return false;
        }
        if ((_queueName == null && that._queueName != null) ||
            (_queueName != null && !_queueName.equals(that._queueName)))
        {
            return false;
        }

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _exchangeName.hashCode();
        result = 29 * result + _exchangeClass.hashCode();
        //result = 29 * result + _destinationName.hashCode();
        if (_queueName != null)
        {
            result = 29 * result + _queueName.hashCode();
        }

        return result;
    }

    public Reference getReference() throws NamingException
    {
        return new Reference(
                this.getClass().getName(),
                new StringRefAddr(this.getClass().getName(), toURL()),
                AMQConnectionFactory.class.getName(),
                null);          // factory location
    }

    public static Destination createDestination(BindingURL binding)
    {
        AMQShortString type = binding.getExchangeClass();

        if (type.equals(ExchangeDefaults.DIRECT_EXCHANGE_CLASS))
        {
            return new AMQQueue(binding);
        }
        else if (type.equals(ExchangeDefaults.TOPIC_EXCHANGE_CLASS))
        {
            return new AMQTopic(binding);
        }
        else if (type.equals(ExchangeDefaults.HEADERS_EXCHANGE_CLASS))
        {
            return new AMQHeadersExchange(binding);
        }
        else
        {
            return new AMQAnyDestination(binding);
        }
    }

    public boolean isBrowseOnly()
    {
        return _browseOnly;
    }

    /**
     * The maximum times a consumer should attempt delivery before rejecting the message
     * without requesting it be re-queued.
     * 
     * @return the Integer value, or null if the option was not set.
     */
    public Integer getMaxDeliveryCount()
    {
        return _maxDeliveryCount;
    }
}
