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
package org.apache.qpid.client;

import org.apache.qpid.url.BindingURL;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.url.URLHelper;
import org.apache.qpid.exchange.ExchangeDefaults;

import javax.naming.Reference;
import javax.naming.NamingException;
import javax.naming.StringRefAddr;
import javax.naming.Referenceable;
import javax.jms.Destination;


public abstract class AMQDestination implements Destination, Referenceable
{
    protected final String _exchangeName;

    protected final String _exchangeClass;

    protected final String _destinationName;

    protected boolean _isDurable;

    protected final boolean _isExclusive;

    protected final boolean _isAutoDelete;

    protected String _queueName;

    protected AMQDestination(String url) throws URLSyntaxException
    {
        this(new AMQBindingURL(url));
    }

    protected AMQDestination(BindingURL binding)
    {
        _exchangeName = binding.getExchangeName();
        _exchangeClass = binding.getExchangeClass();
        _destinationName = binding.getDestinationName();

        _isExclusive = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCLUSIVE));
        _isAutoDelete = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_AUTODELETE));
        _isDurable = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_DURABLE));
        _queueName = binding.getQueueName();
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String destinationName, String queueName)
    {
        this(exchangeName, exchangeClass, destinationName, false, false, queueName);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String destinationName)
    {
        this(exchangeName, exchangeClass, destinationName, false, false, null);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String destinationName, boolean isExclusive,
                             boolean isAutoDelete, String queueName)
    {
        if (destinationName == null)
        {
            throw new IllegalArgumentException("Destination name must not be null");
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
        _destinationName = destinationName;
        _isExclusive = isExclusive;
        _isAutoDelete = isAutoDelete;
        _queueName = queueName;
    }

    public abstract String getEncodedName();

    public boolean isDurable()
    {
        return _isDurable;
    }

    public String getExchangeName()
    {
        return _exchangeName;
    }

    public String getExchangeClass()
    {
        return _exchangeClass;
    }

    public boolean isTopic()
    {
        return ExchangeDefaults.TOPIC_EXCHANGE_NAME.equals(_exchangeName);
    }

    public boolean isQueue()
    {
        return ExchangeDefaults.DIRECT_EXCHANGE_NAME.equals(_exchangeName);
    }

    public String getDestinationName()
    {
        return _destinationName;
    }

    public String getQueueName()
    {
        return _queueName;
    }

    public void setQueueName(String queueName)
    {
        _queueName = queueName;
    }

    public abstract String getRoutingKey();

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

        /*
        return "Destination: " + _destinationName + ", " +
               "Queue Name: " + _queueName + ", Exchange: " + _exchangeName +
               ", Exchange class: " + _exchangeClass + ", Exclusive: " + _isExclusive +
               ", AutoDelete: " + _isAutoDelete + ", Routing  Key: " + getRoutingKey();
         */
    }

    public String toURL()
    {
        StringBuffer sb = new StringBuffer();

        sb.append(_exchangeClass);
        sb.append("://");
        sb.append(_exchangeName);

        sb.append("/");

        if (_destinationName != null)
        {
            sb.append(_destinationName);
        }

        sb.append("/");

        if (_queueName != null)
        {
            sb.append(_queueName);
        }

        sb.append("?");

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

        //remove the last char '?' if there is no options , ',' if there are.
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
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

        if (!_destinationName.equals(that._destinationName))
        {
            return false;
        }
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
        if (_isExclusive != that._isExclusive)
        {
            return false;
        }
        if (_isAutoDelete != that._isAutoDelete)
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
        result = 29 * result + _destinationName.hashCode();
        if (_queueName != null)
        {
            result = 29 * result + _queueName.hashCode();
        }
        result = result * (_isExclusive ? 13 : 7);
        result = result * (_isAutoDelete ? 13 : 7);
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
}
