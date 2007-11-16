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
package org.apache.qpid.url;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

public class AMQBindingURL implements BindingURL
{
    private static final Logger _logger = LoggerFactory.getLogger(AMQBindingURL.class);

    String _url;
    AMQShortString _exchangeClass;
    AMQShortString _exchangeName;
    AMQShortString _destinationName;
    AMQShortString _queueName;
    private HashMap<String, String> _options;

    public AMQBindingURL(String url) throws URLSyntaxException
    {
        // format:
        // <exch_class>://<exch_name>/[<destination>]/[<queue>]?<option>='<value>'[,<option>='<value>']*
        _logger.debug("Parsing URL: " + url);
        _url = url;
        _options = new HashMap<String, String>();

        parseBindingURL();
    }

    private void parseBindingURL() throws URLSyntaxException
    {
        try
        {
            URI connection = new URI(_url);

            String exchangeClass = connection.getScheme();

            if (exchangeClass == null)
            {
                _url = ExchangeDefaults.DIRECT_EXCHANGE_CLASS + "://" + "" + "//" + _url;
                // URLHelper.parseError(-1, "Exchange Class not specified.", _url);
                parseBindingURL();

                return;
            }
            else
            {
                setExchangeClass(exchangeClass);
            }

            String exchangeName = connection.getHost();

            if (exchangeName == null)
            {
                if (getExchangeClass().equals(ExchangeDefaults.DIRECT_EXCHANGE_CLASS))
                {
                    setExchangeName("");
                }
                else
                {
                    throw URLHelper.parseError(-1, "Exchange Name not specified.", _url);
                }
            }
            else
            {
                setExchangeName(exchangeName);
            }

            String queueName;

            if ((connection.getPath() == null) || connection.getPath().equals(""))
            {
                throw URLHelper.parseError(_url.indexOf(_exchangeName.toString()) + _exchangeName.length(),
                    "Destination or Queue requried", _url);
            }
            else
            {
                int slash = connection.getPath().indexOf("/", 1);
                if (slash == -1)
                {
                    throw URLHelper.parseError(_url.indexOf(_exchangeName.toString()) + _exchangeName.length(),
                        "Destination requried", _url);
                }
                else
                {
                    String path = connection.getPath();
                    setDestinationName(path.substring(1, slash));

                    // We don't set queueName yet as the actual value we use depends on options set
                    // when we are dealing with durable subscriptions

                    queueName = path.substring(slash + 1);

                }
            }

            URLHelper.parseOptions(_options, connection.getQuery());

            processOptions();

            // We can now call setQueueName as the URL is full parsed.

            setQueueName(queueName);

            // Fragment is #string (not used)
            _logger.debug("URL Parsed: " + this);

        }
        catch (URISyntaxException uris)
        {

            throw URLHelper.parseError(uris.getIndex(), uris.getReason(), uris.getInput());

        }
    }

    private void setExchangeClass(String exchangeClass)
    {
        setExchangeClass(new AMQShortString(exchangeClass));
    }

    private void setQueueName(String name) throws URLSyntaxException
    {
        setQueueName(new AMQShortString(name));
    }

    private void setDestinationName(String name)
    {
        setDestinationName(new AMQShortString(name));
    }

    private void setExchangeName(String exchangeName)
    {
        setExchangeName(new AMQShortString(exchangeName));
    }

    private void processOptions()
    {
        // this is where we would parse any options that needed more than just storage.
    }

    public String getURL()
    {
        return _url;
    }

    public AMQShortString getExchangeClass()
    {
        return _exchangeClass;
    }

    private void setExchangeClass(AMQShortString exchangeClass)
    {

        _exchangeClass = exchangeClass;
        if (exchangeClass.equals(ExchangeDefaults.TOPIC_EXCHANGE_CLASS))
        {
            setOption(BindingURL.OPTION_EXCLUSIVE, "true");
        }

    }

    public AMQShortString getExchangeName()
    {
        return _exchangeName;
    }

    private void setExchangeName(AMQShortString name)
    {
        _exchangeName = name;
    }

    public AMQShortString getDestinationName()
    {
        return _destinationName;
    }

    private void setDestinationName(AMQShortString name)
    {
        _destinationName = name;
    }

    public AMQShortString getQueueName()
    {
        return _queueName;
    }

    public void setQueueName(AMQShortString name) throws URLSyntaxException
    {
        if (_exchangeClass.equals(ExchangeDefaults.TOPIC_EXCHANGE_CLASS))
        {
            if (Boolean.parseBoolean(getOption(OPTION_DURABLE)))
            {
                if (containsOption(BindingURL.OPTION_CLIENTID) && containsOption(BindingURL.OPTION_SUBSCRIPTION))
                {
                    _queueName =
                        new AMQShortString(getOption(BindingURL.OPTION_CLIENTID + ":" + BindingURL.OPTION_SUBSCRIPTION));
                }
                else
                {
                    throw URLHelper.parseError(-1, "Durable subscription must have values for " + BindingURL.OPTION_CLIENTID
                        + " and " + BindingURL.OPTION_SUBSCRIPTION + ".", _url);

                }
            }
            else
            {
                _queueName = null;
            }
        }
        else
        {
            _queueName = name;
        }

    }

    public String getOption(String key)
    {
        return _options.get(key);
    }

    public void setOption(String key, String value)
    {
        _options.put(key, value);
    }

    public boolean containsOption(String key)
    {
        return _options.containsKey(key);
    }

    public AMQShortString getRoutingKey()
    {
        if (_exchangeClass.equals(ExchangeDefaults.DIRECT_EXCHANGE_CLASS))
        {
            return getQueueName();
        }

        if (containsOption(BindingURL.OPTION_ROUTING_KEY))
        {
            return new AMQShortString(getOption(OPTION_ROUTING_KEY));
        }

        return getDestinationName();
    }

    public void setRoutingKey(AMQShortString key)
    {
        setOption(OPTION_ROUTING_KEY, key.toString());
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append(_exchangeClass);
        sb.append("://");
        sb.append(_exchangeName);
        sb.append('/');
        sb.append(_destinationName);
        sb.append('/');
        sb.append(_queueName);

        sb.append(URLHelper.printOptions(_options));

        return sb.toString();
    }
}
