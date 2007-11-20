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
import org.apache.qpid.url.URLSyntaxException;


public abstract class AMQDestination implements Destination, Referenceable
{
    protected final AMQShortString _exchangeName;

    protected final AMQShortString _exchangeClass;

    protected final AMQShortString _destinationName;

    protected final boolean _isDurable;

    protected final boolean _isExclusive;

    protected final boolean _isAutoDelete;

    private AMQShortString _queueName;

    private String _url;
    private AMQShortString _urlAsShortString;

    private boolean _validated;

    private byte[] _byteEncoding;
    private static final int IS_DURABLE_MASK = 0x1;
    private static final int IS_EXCLUSIVE_MASK = 0x2;
    private static final int IS_AUTODELETE_MASK = 0x4;

    public static final Integer QUEUE_TYPE = Integer.valueOf(1);
    public static final Integer TOPIC_TYPE = Integer.valueOf(2);
    public static final Integer UNKNOWN_TYPE = Integer.valueOf(3);

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
        _queueName = binding.getQueueName() == null ? null : new AMQShortString(binding.getQueueName());
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString destinationName, AMQShortString queueName)
    {
        this(exchangeName, exchangeClass, destinationName, false, false, queueName);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString destinationName)
    {
        this(exchangeName, exchangeClass, destinationName, false, false, null);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString destinationName, boolean isExclusive,
                             boolean isAutoDelete, AMQShortString queueName)
    {
        this(exchangeName, exchangeClass, destinationName, isExclusive, isAutoDelete, queueName, false);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString destinationName, boolean isExclusive,
                             boolean isAutoDelete, AMQShortString queueName, boolean isDurable)
    {
        if (destinationName == null)
        {
            throw new IllegalArgumentException("Destination exchange must not be null");
        }
        if (exchangeName == null)
        {
            throw new IllegalArgumentException("Exchange exchange must not be null");
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
        _isDurable = isDurable;
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

    public AMQShortString getDestinationName()
    {
        return _destinationName;
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
        _byteEncoding = null;
    }

    public abstract AMQShortString getRoutingKey();

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

    public boolean isValidated()
    {
        return _validated;
    }

    public void setValidated(boolean validated)
    {
        _validated = validated;
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

            //removeKey the last char '?' if there is no options , ',' if there are.
            sb.deleteCharAt(sb.length() - 1);
            url = sb.toString();
            _url = url;
            _urlAsShortString = new AMQShortString(url);
        }
        return url;
    }

    public byte[] toByteEncoding()
    {
        byte[] encoding = _byteEncoding;
        if(encoding == null)
        {
            int size = _exchangeClass.length() + 1 +
                       _exchangeName.length() + 1 +
                       (_destinationName == null ? 0 : _destinationName.length()) + 1 +
                       (_queueName == null ? 0 : _queueName.length()) + 1 +
                       1;
            encoding = new byte[size];
            int pos = 0;

            pos = _exchangeClass.writeToByteArray(encoding, pos);
            pos = _exchangeName.writeToByteArray(encoding, pos);
            if(_destinationName == null)
            {
                encoding[pos++] = (byte)0;
            }
            else
            {
                pos = _destinationName.writeToByteArray(encoding,pos);
            }
            if(_queueName == null)
            {
                encoding[pos++] = (byte)0;
            }
            else
            {
                pos = _queueName.writeToByteArray(encoding,pos);
            }
            byte options = 0;
            if(_isDurable)
            {
                options |= IS_DURABLE_MASK;
            }
            if(_isExclusive)
            {
                options |= IS_EXCLUSIVE_MASK;
            }
            if(_isAutoDelete)
            {
                options |= IS_AUTODELETE_MASK;
            }
            encoding[pos] = options;


            _byteEncoding = encoding;

        }
        return encoding;
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


    public static Destination createDestination(byte[] byteEncodedDestination)
    {
        AMQShortString exchangeClass;
        AMQShortString exchangeName;
        AMQShortString destinationName;
        AMQShortString queueName;
        boolean isDurable;
        boolean isExclusive;
        boolean isAutoDelete;

        int pos = 0;
        exchangeClass = AMQShortString.readFromByteArray(byteEncodedDestination, pos);
        pos+= exchangeClass.length() + 1;
        exchangeName =  AMQShortString.readFromByteArray(byteEncodedDestination, pos);
        pos+= exchangeName.length() + 1;
        destinationName =  AMQShortString.readFromByteArray(byteEncodedDestination, pos);
        pos+= (destinationName == null ? 0 : destinationName.length()) + 1;
        queueName =  AMQShortString.readFromByteArray(byteEncodedDestination, pos);
        pos+= (queueName == null ? 0 : queueName.length()) + 1;
        int options = byteEncodedDestination[pos];
        isDurable = (options & IS_DURABLE_MASK) != 0;
        isExclusive = (options & IS_EXCLUSIVE_MASK) != 0;
        isAutoDelete = (options & IS_AUTODELETE_MASK) != 0;

        if (exchangeClass.equals(ExchangeDefaults.DIRECT_EXCHANGE_CLASS))
        {
            return new AMQQueue(exchangeName,destinationName,queueName,isExclusive,isAutoDelete,isDurable);
        }
        else if (exchangeClass.equals(ExchangeDefaults.TOPIC_EXCHANGE_CLASS))
        {
            return new AMQTopic(exchangeName,destinationName,isAutoDelete,queueName,isDurable);
        }
        else if (exchangeClass.equals(ExchangeDefaults.HEADERS_EXCHANGE_CLASS))
        {
            return new AMQHeadersExchange(destinationName);
        }
        else
        {
            throw new IllegalArgumentException("Unknown Exchange Class:" + exchangeClass);
        }



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
            throw new IllegalArgumentException("Unknown Exchange Class:" + type + " in binding:" + binding);
        }
    }
}
